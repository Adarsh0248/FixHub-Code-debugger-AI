package com.razeef.bugbrother.indexing.service;

import com.razeef.bugbrother.chunking.model.RepositoryChunk;
import com.razeef.bugbrother.chunking.service.DeterministicRepositoryChunker;
import com.razeef.bugbrother.events.IndexRepositoryCommandV2;
import com.razeef.bugbrother.indexing.client.ManifestSubmissionClient;
import com.razeef.bugbrother.messaging.service.TaskStatusPublisher;
import com.razeef.bugbrother.messaging.exception.TaskStatusPublicationException;
import com.razeef.bugbrother.source.model.RepositorySourceFile;
import com.razeef.bugbrother.source.service.GitHubRevisionSourceService;
import com.razeef.bugbrother.vector.service.VectorSearchService;
import com.razeef.bugbrother.indexing.model.PreparedChunkSubmission;
import com.razeef.bugbrother.indexing.model.IndexSubmissionRecoveryState;
import com.razeef.bugbrother.indexing.model.PendingChunkSubmission;
import com.razeef.bugbrother.indexing.model.TaskExecutionState;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientException;
import java.util.UUID;
import java.util.List;
import java.nio.charset.StandardCharsets;

@Service
public class IndexWorkerService {

    private final GitHubRevisionSourceService sourceService;
    private final DeterministicRepositoryChunker chunker;
    private final ManifestSubmissionClient manifestClient;
    private final VectorSearchService vectorSearchService;
    private final TaskStatusPublisher statusPublisher;

    public IndexWorkerService(
            GitHubRevisionSourceService sourceService,
            DeterministicRepositoryChunker chunker,
            ManifestSubmissionClient manifestClient,
            VectorSearchService vectorSearchService,
            TaskStatusPublisher statusPublisher
    ) {
        this.sourceService = sourceService;
        this.chunker = chunker;
        this.manifestClient = manifestClient;
        this.vectorSearchService = vectorSearchService;
        this.statusPublisher = statusPublisher;
    }

    @KafkaListener(
            topics = "code-guardian-index-tasks",
            groupId = "code-guardian-group"
    )
    public void consumeIndexTask(
            IndexRepositoryCommandV2 command
    ) {
        long sequence = 1;
        boolean vectorSubmissionStarted = false;
        boolean recoveryStateKnown = false;
        boolean replayingSubmission = false;

        try {
            validateCommand(command);

            TaskExecutionState taskState = manifestClient.fetchTaskState(
                    command.taskId());
            if (taskState == null
                    || !command.taskId().equals(taskState.taskId())) {
                throw new IllegalStateException(
                        "Index task state does not match the command");
            }
            if (taskState.terminal()) {
                return;
            }
            sequence = taskState.eventSequence() + 1;

            statusPublisher.running(
                    command.taskId(),
                    sequence++,
                    "STARTING",
                    "Worker accepted the index task"
            );

            if (!command.buildRequired()) {
                handleReusableGeneration(command, sequence);
                return;
            }

            IndexSubmissionRecoveryState recoveryState =
                    manifestClient.fetchRecoveryState(command.generationId());
            if (recoveryState == null
                    || !command.vectorClientId().equals(
                            recoveryState.vectorClientId())) {
                throw new IllegalStateException(
                        "Index recovery state does not match the command");
            }
            recoveryStateKnown = true;

            if (!"BUILDING".equals(recoveryState.status())) {
                return;
            }

            if (recoveryState.vectorSubmissionStarted()) {
                vectorSubmissionStarted = true;
                replayingSubmission = true;
                resumePendingSubmissions(command);
                return;
            }

            statusPublisher.running(
                    command.taskId(),
                    sequence++,
                    "FETCHING",
                    "Fetching the immutable repository revision "
                            + command.commitSha()
            );

            List<RepositorySourceFile> files =
                    sourceService.fetchRevision(
                            command.owner(),
                            command.repo(),
                            command.commitSha(),
                            command.githubToken()
                    );

            statusPublisher.running(
                    command.taskId(),
                    sequence++,
                    "CHUNKING",
                    "Creating deterministic chunks from "
                            + files.size()
                            + " source files"
            );

            List<RepositoryChunk> chunks =
                    chunker.chunkRevision(
                            command.repositoryId(),
                            command.owner()
                                    + "/"
                                    + command.repo(),
                            command.commitSha(),
                            files
                    );

            if (chunks.isEmpty()) {
                throw new IllegalStateException(
                        "The selected revision produced no indexable chunks"
                );
            }

            List<PreparedChunkSubmission> submissions =
                chunks.stream()
                        .map(chunk ->
                                new PreparedChunkSubmission(
                                        submissionId(command.generationId(),
                                                chunk.chunkId()),
                                        chunk
                                )
                        )
                        .toList();

            statusPublisher.running(
                    command.taskId(),
                    sequence++,
                    "STORING_MANIFEST",
                    "Storing "
                            + files.size()
                            + " files and "
                            + chunks.size()
                            + " chunks in the manifest"
            );

            manifestClient.submitManifest(
                    command.generationId(),
                    files,
                    chunks
            );

            manifestClient.registerSubmissions(
                        command.generationId(),
                        submissions
                );

            statusPublisher.running(
                    command.taskId(),
                    sequence++,
                    "SUBMITTING",
                    "Submitting "
                            + chunks.size()
                            + " chunks to the vector gateway"
            );

            vectorSubmissionStarted = true;
            manifestClient.markVectorSubmissionStarted(
                    command.generationId()
            );
            VectorSearchService.ChunkSubmissionResult result =
                   vectorSearchService.submitChunks(
                                command.vectorClientId(),
                                submissions
                        );

            statusPublisher.running(
                    command.taskId(),
                    sequence,
                    "WAITING_FOR_INDEX",
                    "Gateway accepted "
                            + result.submittedChunks()
                            + " of "
                            + result.totalChunks()
                            + " chunks; waiting for final vector-index "
                            + "acknowledgements"
            );
        } catch (Exception exception) {
            if (!recoveryStateKnown
                    || exception instanceof TaskStatusPublicationException
                    || (replayingSubmission
                            && exception instanceof WebClientException)) {
                throw exception;
            }

            String userMessage = vectorSubmissionStarted
                    ? "Indexing failed after vector submission began. "
                            + "Automatic cleanup was scheduled. Retry indexing. Cause: "
                            + safeMessage(exception)
                    : "Indexing failed before vector submission. "
                            + "Incomplete data was discarded. Retry indexing. Cause: "
                            + safeMessage(exception);

            if (command != null
                    && command.buildRequired()
                    && command.generationId() != null) {
                reportGenerationFailure(
                        command,
                        new IllegalStateException(
                                userMessage,
                                exception
                        ),
                        vectorSubmissionStarted
                );
            }

            if (command != null
                    && command.taskId() != null) {
                statusPublisher.failed(
                        command.taskId(),
                        sequence,
                        vectorSubmissionStarted
                                ? "INDEX_FAILED_CLEANUP_SCHEDULED"
                                : "INDEX_PREPARATION_FAILED",
                        new IllegalStateException(
                                userMessage,
                                exception
                        )
                );
            }
        }
    }

    private void handleReusableGeneration(
            IndexRepositoryCommandV2 command,
            long sequence
    ) {
        if ("READY".equals(command.generationStatus())) {
            statusPublisher.completed(
                    command.taskId(),
                    sequence,
                    1,
                    1,
                    "The selected repository revision is already indexed",
                    "Reused ready index generation "
                            + command.generationId()
            );

            return;
        }

        statusPublisher.running(
                command.taskId(),
                sequence,
                "WAITING_FOR_INDEX",
                "Index generation "
                        + command.generationId()
                        + " is already being built"
        );
    }

    private void validateCommand(
            IndexRepositoryCommandV2 command
    ) {
        if (command == null) {
            throw new IllegalArgumentException(
                    "Index command is required"
            );
        }

        if (command.taskId() == null
                || command.generationId() == null) {
            throw new IllegalArgumentException(
                    "Task ID and generation ID are required"
            );
        }

        if (command.repositoryId() == null
                || command.repositoryId() <= 0) {
            throw new IllegalArgumentException(
                    "repositoryId must be positive"
            );
        }

        requireText(command.owner(), "owner");
        requireText(command.repo(), "repo");
        requireText(command.branch(), "branch");
        requireText(command.commitSha(), "commitSha");
        requireText(
                command.vectorClientId(),
                "vectorClientId"
        );
        requireText(command.modelId(), "modelId");
        requireText(
                command.chunkerVersion(),
                "chunkerVersion"
        );
        requireText(
                command.generationStatus(),
                "generationStatus"
        );

        if (command.embeddingDimension() <= 0) {
            throw new IllegalArgumentException(
                    "embeddingDimension must be positive"
            );
        }

        if (command.buildRequired()
                && !DeterministicRepositoryChunker
                        .CHUNKER_VERSION
                        .equals(command.chunkerVersion())) {
            throw new IllegalStateException(
                    "Worker chunker version "
                            + DeterministicRepositoryChunker
                                    .CHUNKER_VERSION
                            + " does not match generation chunker version "
                            + command.chunkerVersion()
            );
        }

        if (command.buildRequired()) {
            requireText(
                    command.githubToken(),
                    "githubToken"
            );
        }
    }

    private void reportGenerationFailure(
            IndexRepositoryCommandV2 command,
            Exception failure,
            boolean vectorsMayExist
    ) {
        try {
            if (vectorsMayExist) {
                manifestClient.failGeneration(
                        command.generationId(),
                        "INDEX_TASK_FAILED",
                        safeMessage(failure),
                        true
                );
            } else {
                manifestClient.discardPreparedGeneration(
                        command.generationId()
                );
            }
        } catch (RuntimeException reportingFailure) {
            failure.addSuppressed(reportingFailure);
        }
    }

    private UUID submissionId(UUID generationId, String chunkId) {
        return UUID.nameUUIDFromBytes((generationId + ":" + chunkId)
                .getBytes(StandardCharsets.UTF_8));
    }

    private void resumePendingSubmissions(IndexRepositoryCommandV2 command) {
        String afterChunkId = "";
        while (true) {
            List<PendingChunkSubmission> page =
                    manifestClient.fetchPendingSubmissions(
                            command.generationId(), afterChunkId);
            if (page.isEmpty()) {
                return;
            }

            for (PendingChunkSubmission submission : page) {
                vectorSearchService.submitStoredChunk(
                        command.vectorClientId(), submission);
            }
            afterChunkId = page.get(page.size() - 1).chunkId();
        }
    }

    private void requireText(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " is required"
            );
        }
    }

    private String safeMessage(
            Throwable exception
    ) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            message = exception
                    .getClass()
                    .getSimpleName();
        }

        return message.length() <= 1000
                ? message
                : message.substring(0, 1000);
    }
}
