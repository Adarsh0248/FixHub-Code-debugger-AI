package com.razeef.bugbrother.services;

import com.razeef.bugbrother.Wrappers.GitAiLayer;
import com.razeef.bugbrother.events.DebugRepositoryCommandV1;
import com.razeef.bugbrother.parsers.FixedfileParser;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DebugWorkerService {

    private static final int RELATED_FILES_LIMIT = 5;

    private final GitAiLayer gitAiLayer;
    private final FixedfileParser fixedfileParser;
    private final CommitService commitService;
    private final VectorSearchService vectorSearchService;
    private final TaskStatusPublisher statusPublisher;

    public DebugWorkerService(
            GitAiLayer gitAiLayer,
            FixedfileParser fixedfileParser,
            CommitService commitService,
            VectorSearchService vectorSearchService,
            TaskStatusPublisher statusPublisher
    ) {
        this.gitAiLayer = gitAiLayer;
        this.fixedfileParser = fixedfileParser;
        this.commitService = commitService;
        this.vectorSearchService = vectorSearchService;
        this.statusPublisher = statusPublisher;
    }

    @KafkaListener(
            topics = "code-guardian-tasks",
            groupId = "code-guardian-group"
    )
    public void consumeTask(DebugRepositoryCommandV1 command) {
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

            List<CommitService.FixedFile> relatedFiles =
                    vectorSearchService.searchRelated(
                            command.owner(),
                            command.repo(),
                            command.githubToken(),
                            command.errorQuery(),
                            List.of(),
                            RELATED_FILES_LIMIT
                    );

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
                    "Generating corrected files"
            );

            String aiResponse = gitAiLayer.askAiDebug(
                    relatedFiles,
                    command.errorQuery()
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