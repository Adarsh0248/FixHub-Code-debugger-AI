package com.razeef.bugbrother.debug.service;

import com.razeef.bugbrother.github.service.CommitService;
import com.razeef.bugbrother.messaging.service.TaskStatusPublisher;
import com.razeef.bugbrother.vector.service.VectorSearchService;
import com.razeef.bugbrother.retrieval.client.IndexContextClient;
import com.razeef.bugbrother.retrieval.exception.ContextBudgetExceededException;
import com.razeef.bugbrother.retrieval.exception.ContextExpansionException;
import com.razeef.bugbrother.retrieval.model.ContextBundle;
import com.razeef.bugbrother.retrieval.model.VectorContext;
import com.razeef.bugbrother.retrieval.model.VectorSearchHit;
import com.razeef.bugbrother.retrieval.model.DependencyExpansion;
import com.razeef.bugbrother.retrieval.service.ContextBundleService;
import com.razeef.bugbrother.debug.model.DebugMode;
import com.razeef.bugbrother.debug.model.ModelGenerationResult;
import com.razeef.bugbrother.events.DebugRepositoryCommandV3;
import com.razeef.bugbrother.debug.parser.FixedfileParser;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DebugWorkerService {

    private static final int INITIAL_VECTOR_HIT_LIMIT = 12;
    private static final int DEPENDENCY_DEPTH = 2;
    private static final int DEPENDENCY_FILE_LIMIT = 12;

    private final FixedfileParser fixedfileParser;
    private final CommitService commitService;
    private final VectorSearchService vectorSearchService;
    private final TaskStatusPublisher statusPublisher;
    private final IndexContextClient indexContextClient;
    private final ContextBundleService contextBundleService;
    private final IterativeModelGenerationService modelGenerationService;

    public DebugWorkerService(
        FixedfileParser fixedfileParser,
        CommitService commitService,
        VectorSearchService vectorSearchService,
        TaskStatusPublisher statusPublisher,
        IndexContextClient indexContextClient,
        ContextBundleService contextBundleService,
        IterativeModelGenerationService modelGenerationService
        ) {
        this.fixedfileParser = fixedfileParser;
        this.commitService = commitService;
        this.vectorSearchService = vectorSearchService;
        this.statusPublisher = statusPublisher;
        this.indexContextClient = indexContextClient;
        this.contextBundleService = contextBundleService;
        this.modelGenerationService = modelGenerationService;
        }

    @KafkaListener(
            topics = "code-guardian-debug-tasks-v3",
            groupId = "code-guardian-debug-v3-group"
    )
    public void consumeTask(DebugRepositoryCommandV3 command) {
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

            if (vectorContext.files().isEmpty()) {
                throw new IllegalStateException(
                        "No related files were found. "
                                + "Index the repository before debugging."
                );
            }

            ContextBundle contextBundle = contextBundleService.build(
                    vectorContext,
                    dependencyExpansion,
                    command.errorQuery()
            );

            statusPublisher.running(
                    command.taskId(),
                    sequence++,
                    "GENERATING",
                    (command.mode() == DebugMode.GUIDE_ONLY
                            ? "Generating debugging guidance using "
                            : "Generating corrected files using ")
                            + contextBundle.primaryFiles().size()
                            + " primary file(s) and "
                            + contextBundle.supportingFiles().size()
                            + " supporting file(s); estimated context "
                            + contextBundle.estimatedTokens()
                            + " tokens"
            );

            ModelGenerationResult generationResult =
                    modelGenerationService.generate(
                            command.mode(),
                            command.generationId(),
                            command.vectorClientId(),
                            vectorContext,
                            dependencyExpansion,
                            command.errorQuery()
                    );

            String aiResponse = generationResult.response();

            if (command.mode() == DebugMode.GUIDE_ONLY) {
                statusPublisher.completedWithResult(
                        command.taskId(),
                        sequence,
                        1,
                        1,
                        "Debugging guidance is ready",
                        null,
                        null,
                        null,
                        aiResponse.trim(),
                        "No repository files were changed; model rounds: "
                                + generationResult.rounds()
                );
                return;
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

            CommitService.CommitResult commitResult =
                    commitService.createFixBranchAndCommitWithLogging(
                    command.owner(),
                    command.repo(),
                    command.baseCommitSha(),
                    files,
                    command.githubToken()
            );

            statusPublisher.completedWithResult(
                    command.taskId(),
                    sequence,
                    files.size(),
                    files.size(),
                    "Fix branch created with "
                            + files.size()
                            + " changed file(s)",
                    commitResult.branchName(),
                    commitResult.commitSha(),
                    commitResult.url(),
                    extractExplanation(aiResponse, files.size()),
                    "Model rounds: "
                            + generationResult.rounds()
                            + "; patch validation will be added in Phase 7"
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
        if (exception instanceof ContextBudgetExceededException) {
            return "CONTEXT_BUDGET_EXCEEDED";
        }

        if (exception instanceof ContextExpansionException) {
            return "CONTEXT_EXPANSION_FAILED";
        }

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

    private String extractExplanation(
            String aiResponse,
            int changedFileCount
    ) {
        String marker = "EXPLANATION:";
        int markerIndex = aiResponse.lastIndexOf(marker);

        if (markerIndex >= 0) {
            String explanation = aiResponse
                    .substring(markerIndex + marker.length())
                    .trim();

            if (!explanation.isBlank()) {
                return explanation;
            }
        }

        return "The model corrected "
                + changedFileCount
                + " file(s). A structured explanation was not returned.";
    }
}
