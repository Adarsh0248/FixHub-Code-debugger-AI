package com.razeef.bugbrother.debug.service;

import com.razeef.bugbrother.github.service.CommitService;
import com.razeef.bugbrother.messaging.service.TaskStatusPublisher;
import com.razeef.bugbrother.vector.service.VectorSearchService;
import com.razeef.bugbrother.retrieval.client.IndexContextClient;
import com.razeef.bugbrother.retrieval.model.VectorContext;
import com.razeef.bugbrother.retrieval.model.VectorSearchHit;
import com.razeef.bugbrother.retrieval.model.DependencyExpansion;
import com.razeef.bugbrother.debug.ai.GitAiLayer;
import com.razeef.bugbrother.events.DebugRepositoryCommandV2;
import com.razeef.bugbrother.debug.parser.FixedfileParser;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DebugWorkerService {

    private static final int INITIAL_VECTOR_HIT_LIMIT = 12;
    private static final int DEPENDENCY_DEPTH = 2;
    private static final int DEPENDENCY_FILE_LIMIT = 12;

    private final GitAiLayer gitAiLayer;
    private final FixedfileParser fixedfileParser;
    private final CommitService commitService;
    private final VectorSearchService vectorSearchService;
    private final TaskStatusPublisher statusPublisher;
    private final IndexContextClient indexContextClient;

    public DebugWorkerService(
        GitAiLayer gitAiLayer,
        FixedfileParser fixedfileParser,
        CommitService commitService,
        VectorSearchService vectorSearchService,
        TaskStatusPublisher statusPublisher,
        IndexContextClient indexContextClient
        ) {
        this.gitAiLayer = gitAiLayer;
        this.fixedfileParser = fixedfileParser;
        this.commitService = commitService;
        this.vectorSearchService = vectorSearchService;
        this.statusPublisher = statusPublisher;
        this.indexContextClient = indexContextClient;
        }

    @KafkaListener(
            topics = "code-guardian-debug-tasks-v2",
            groupId = "code-guardian-debug-v2-group"
    )
    public void consumeTask(DebugRepositoryCommandV2 command) {
        long sequence = 1;

        try {
            statusPublisher.running(
                    command.taskId(),
                    sequence++,
                    "STARTING",
                    "Worker accepted the debug task"
            );

            statusPublisher.running(
                    command.taskId(),
                    sequence++,
                    "RETRIEVING",
                    "Searching the repository index"
            );

            List<VectorSearchHit> hits =
        vectorSearchService.searchGeneration(
                command.vectorClientId(),
                command.errorQuery(),
                INITIAL_VECTOR_HIT_LIMIT
        );

        if (hits.isEmpty()) {
        throw new IllegalStateException(
                "No related chunks were found in the active index"
        );
        }

        VectorContext vectorContext =
                indexContextClient.resolve(
                        command.generationId(),
                        command.vectorClientId(),
                        hits
                );

        if (!vectorContext.commitSha()
                .equals(command.baseCommitSha())) {
        throw new IllegalStateException(
                "Retrieved context belongs to another commit"
        );
        }

        List<CommitService.FixedFile> relatedFiles =
                vectorContext.files()
                        .stream()
                        .map(file ->
                                new CommitService.FixedFile(
                                        file.path(),
                                        file.content()
                                )
                        )
                        .toList();

        DependencyExpansion dependencyExpansion =
                indexContextClient.expandDependencies(
                        command.generationId(),
                        command.vectorClientId(),
                        vectorContext.files()
                                .stream()
                                .map(file -> file.fileId())
                                .toList(),
                        DEPENDENCY_DEPTH,
                        DEPENDENCY_FILE_LIMIT
                );

        if (!dependencyExpansion.generationId()
                .equals(command.generationId())) {
            throw new IllegalStateException(
                    "Dependency expansion belongs to another generation"
            );
        }

        List<CommitService.FixedFile> supportingFiles =
                dependencyExpansion.files()
                        .stream()
                        .map(file ->
                                new CommitService.FixedFile(
                                        file.path(),
                                        file.content()
                                )
                        )
                        .toList();

            if (relatedFiles.isEmpty()) {
                throw new IllegalStateException(
                        "No related files were found. "
                                + "Index the repository before debugging."
                );
            }

            statusPublisher.running(
                    command.taskId(),
                    sequence++,
                    "GENERATING",
                    "Generating corrected files using "
                            + relatedFiles.size()
                            + " vector-matched file(s) and "
                            + supportingFiles.size()
                            + " dependency file(s)"
            );

            String aiResponse = gitAiLayer.askAiDebug(
                    relatedFiles,
                    command.errorQuery(),
                    supportingFiles
            );

            if (aiResponse == null || aiResponse.isBlank()) {
                throw new IllegalStateException(
                        "The model returned an empty response"
                );
            }

            List<CommitService.FixedFile> files =
                    fixedfileParser.parseFixedFiles(aiResponse);

            if (files.isEmpty()) {
                throw new IllegalStateException(
                        "The model response contained no valid corrected files"
                );
            }

            statusPublisher.running(
                    command.taskId(),
                    sequence++,
                    "COMMITTING",
                    "Creating the fix branch"
            );

            commitService.createFixBranchAndCommitWithLogging(
                    command.owner(),
                    command.repo(),
                    files,
                    command.githubToken()
            );

            statusPublisher.completed(
                    command.taskId(),
                    sequence,
                    files.size(),
                    files.size(),
                    "Fix branch created with "
                            + files.size()
                            + " changed file(s)",
                    "Patch validation will be added in Phase 7"
            );
        } catch (Exception exception) {
            statusPublisher.failed(
                    command.taskId(),
                    sequence,
                    classify(exception),
                    exception
            );
        }
    }

    private String classify(Exception exception) {
        String message = exception.getMessage() == null
                ? ""
                : exception.getMessage();

        if (message.contains("No related files")) {
            return "NO_RETRIEVAL_RESULTS";
        }

        if (message.contains("empty response")
                || message.contains("no valid corrected files")) {
            return "MODEL_RESPONSE_INVALID";
        }

        if (message.contains("GitHub")) {
            return "GITHUB_WRITE_FAILED";
        }

        return "DEBUG_TASK_FAILED";
    }
}
