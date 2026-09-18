package com.razeef.bugbrother.services;

import com.razeef.bugbrother.events.IndexRepositoryCommandV1;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class IndexWorkerService {

    private final VectorSearchService vectorSearchService;
    private final TaskStatusPublisher statusPublisher;

    public IndexWorkerService(
            VectorSearchService vectorSearchService,
            TaskStatusPublisher statusPublisher
    ) {
        this.vectorSearchService = vectorSearchService;
        this.statusPublisher = statusPublisher;
    }

    @KafkaListener(
            topics = "code-guardian-index-tasks",
            groupId = "code-guardian-group"
    )
    public void consumeIndexTask(IndexRepositoryCommandV1 command) {
        long sequence = 1;

        try {
            statusPublisher.running(
                    command.taskId(),
                    sequence++,
                    "STARTING",
                    "Worker accepted the index task"
            );

            statusPublisher.running(
                    command.taskId(),
                    sequence++,
                    "FETCHING",
                    "Fetching repository files"
            );

            VectorSearchService.IndexResult result =
                    vectorSearchService.indexRepoFiles(
                            command.owner(),
                            command.repo(),
                            command.githubToken()
                    );

            if (result.failedFiles() > 0) {
                throw new IllegalStateException(
                        result.failedFiles()
                                + " vector submission(s) failed"
                );
            }

            statusPublisher.completed(
                    command.taskId(),
                    sequence,
                    result.submittedFiles(),
                    result.totalFiles(),
                    "Submitted "
                            + result.submittedFiles()
                            + " file(s) for vector indexing",
                    "Gateway acknowledgement and true index readiness "
                            + "will be added in Phase 4"
            );
        } catch (Exception exception) {
            statusPublisher.failed(
                    command.taskId(),
                    sequence,
                    "INDEX_TASK_FAILED",
                    exception
            );
        }
    }
}