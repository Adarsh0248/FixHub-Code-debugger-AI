package com.razeef.bugbrother.indexes.service;

import com.razeef.bugbrother.indexes.dto.event.VectorIndexResultEvent;
import com.razeef.bugbrother.indexes.model.ActiveIndexGenerationEntity;
import com.razeef.bugbrother.indexes.model.IndexGenerationEntity;
import com.razeef.bugbrother.indexes.model.IndexGenerationStatus;
import com.razeef.bugbrother.indexes.model.IndexedChunkEntity;
import com.razeef.bugbrother.indexes.repository.ActiveIndexGenerationRepository;
import com.razeef.bugbrother.indexes.repository.IndexGenerationRepository;
import com.razeef.bugbrother.indexes.repository.IndexedChunkRepository;
import com.razeef.bugbrother.repositories.repository.RepositoryRepository;
import com.razeef.bugbrother.tasks.model.TaskEntity;
import com.razeef.bugbrother.tasks.model.TaskStatus;
import com.razeef.bugbrother.tasks.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class IndexAcknowledgementService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    IndexAcknowledgementService.class
            );

    private static final List<TaskStatus> TERMINAL_STATUSES =
            List.of(
                    TaskStatus.COMPLETED,
                    TaskStatus.FAILED
            );

    private final IndexedChunkRepository chunkRepository;
    private final IndexGenerationRepository generationRepository;
    private final ActiveIndexGenerationRepository activeRepository;
    private final RepositoryRepository repositoryRepository;
    private final TaskRepository taskRepository;

    public IndexAcknowledgementService(
            IndexedChunkRepository chunkRepository,
            IndexGenerationRepository generationRepository,
            ActiveIndexGenerationRepository activeRepository,
            RepositoryRepository repositoryRepository,
            TaskRepository taskRepository
    ) {
        this.chunkRepository = chunkRepository;
        this.generationRepository = generationRepository;
        this.activeRepository = activeRepository;
        this.repositoryRepository = repositoryRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public void apply(VectorIndexResultEvent event) {
        UUID submissionEventId = parseCorrelationId(event);
        if (submissionEventId == null) {
            return;
        }

        IndexedChunkEntity chunk = chunkRepository
                .findLockedBySubmissionEventId(submissionEventId)
                .orElse(null);

        // The gateway is reusable by other clients. Their correlated
        // result events legitimately have no BugBrother manifest row.
        if (chunk == null) {
            LOGGER.debug(
                    "Ignoring vector result for unknown correlation ID {}",
                    submissionEventId
            );
            return;
        }

        IndexGenerationEntity generation = generationRepository
                .findLockedByGenerationId(chunk.getGenerationId())
                .orElseThrow(() -> new IllegalStateException(
                        "Generation not found for chunk: "
                                + chunk.getChunkId()
                ));

        if (generation.getStatus()
                != IndexGenerationStatus.BUILDING) {
            return;
        }

        String identityError = identityError(
                event,
                generation,
                chunk
        );
        if (identityError != null) {
            recordFailed(
                    generation,
                    chunk,
                    "VECTOR_RESULT_IDENTITY_MISMATCH",
                    identityError
            );
            return;
        }

        switch (event.outcome()) {
            case "INDEXED" -> recordIndexed(
                    generation,
                    chunk
            );
            case "FAILED" -> recordFailed(
                    generation,
                    chunk,
                    safe(
                            event.errorCode(),
                            "VECTOR_INDEX_FAILED"
                    ),
                    safe(
                            event.errorMessage(),
                            "Vector indexing failed"
                    )
            );
            default -> recordFailed(
                    generation,
                    chunk,
                    "UNSUPPORTED_VECTOR_RESULT",
                    "Unsupported vector result outcome: "
                            + event.outcome()
            );
        }
    }

    private void recordIndexed(
            IndexGenerationEntity generation,
            IndexedChunkEntity chunk
    ) {
        if (!chunk.markIndexed()) {
            return;
        }

        generation.recordIndexedChunk();
        updateTaskProgress(generation);

        if (generation.getIndexedChunks()
                == generation.getExpectedChunks()
                && generation.getFailedChunks() == 0) {
            generation.markReady();
            activate(generation);
            completeTasks(generation);
        }
    }

    private void recordFailed(
            IndexGenerationEntity generation,
            IndexedChunkEntity chunk,
            String errorCode,
            String errorMessage
    ) {
        if (!chunk.markFailed(errorCode, errorMessage)) {
            return;
        }

        generation.recordFailedChunk();
        generation.markFailed(errorCode, errorMessage);
        generation.beginCleanup();
        failTasks(generation, errorCode, errorMessage);
    }

    private void activate(IndexGenerationEntity generation) {
        repositoryRepository
                .findLockedByUserIdAndGithubRepositoryId(
                        generation.getUserId(),
                        generation.getRepositoryId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Repository not found while activating generation "
                                + generation.getGenerationId()
                ));

        ActiveIndexGenerationEntity active = activeRepository
                .findLocked(
                        generation.getUserId(),
                        generation.getRepositoryId(),
                        generation.getBranch()
                )
                .orElse(null);

        if (active == null) {
            activeRepository.save(
                    ActiveIndexGenerationEntity.create(
                            generation.getUserId(),
                            generation.getRepositoryId(),
                            generation.getBranch(),
                            generation.getGenerationId()
                    )
            );
            return;
        }

        UUID previousGenerationId = active.getGenerationId();
        if (previousGenerationId.equals(
                generation.getGenerationId()
        )) {
            return;
        }

        IndexGenerationEntity previous = generationRepository
                .findLockedByGenerationId(previousGenerationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Active generation row points to a missing generation: "
                                + previousGenerationId
                ));

        active.pointTo(generation.getGenerationId());

        if (previous.getStatus()
                == IndexGenerationStatus.READY) {
            previous.retire();
        }
    }

    private void updateTaskProgress(
            IndexGenerationEntity generation
    ) {
        for (TaskEntity task : waitingTasks(
                generation.getGenerationId()
        )) {
            task.updateIndexProgress(
                    generation.getIndexedChunks(),
                    generation.getExpectedChunks()
            );
        }
    }

    private void completeTasks(
            IndexGenerationEntity generation
    ) {
        for (TaskEntity task : waitingTasks(
                generation.getGenerationId()
        )) {
            task.completeIndexGeneration(
                    generation.getExpectedChunks()
            );
        }
    }

    private void failTasks(
            IndexGenerationEntity generation,
            String errorCode,
            String errorMessage
    ) {
        for (TaskEntity task : waitingTasks(
                generation.getGenerationId()
        )) {
            task.failIndexGeneration(
                    errorCode,
                    errorMessage
            );
        }
    }

    private List<TaskEntity> waitingTasks(UUID generationId) {
        return taskRepository
                .findLockedByGenerationIdAndStatusNotIn(
                        generationId,
                        TERMINAL_STATUSES
                );
    }

    private UUID parseCorrelationId(
            VectorIndexResultEvent event
    ) {
        if (event == null) {
            LOGGER.warn("Ignoring null vector result event");
            return null;
        }
        if (event.schemaVersion() != 1) {
            LOGGER.warn(
                    "Ignoring vector result schema version {}",
                    event.schemaVersion()
            );
            return null;
        }
        if (event.correlationId() == null
                || event.correlationId().isBlank()) {
            LOGGER.debug(
                    "Ignoring uncorrelated vector result event"
            );
            return null;
        }

        try {
            return UUID.fromString(event.correlationId());
        } catch (IllegalArgumentException exception) {
            LOGGER.debug(
                    "Ignoring non-BugBrother correlation ID {}",
                    event.correlationId()
            );
            return null;
        }
    }

    private String identityError(
            VectorIndexResultEvent event,
            IndexGenerationEntity generation,
            IndexedChunkEntity chunk
    ) {
        if (event.clientId() == null
                || event.clientId().isBlank()) {
            return "Vector result client ID is missing";
        }
        if (event.vectorLabel() == null
                || event.vectorLabel().isBlank()) {
            return "Vector result label is missing";
        }
        if (event.outcome() == null
                || event.outcome().isBlank()) {
            return "Vector result outcome is missing";
        }
        if (!generation.getVectorClientId().equals(
                event.clientId()
        )) {
            return "Vector result client ID does not match generation";
        }
        if (!chunk.getVectorLabel().equals(
                event.vectorLabel()
        )) {
            return "Vector result label does not match chunk";
        }
        return null;
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value;
    }
}
