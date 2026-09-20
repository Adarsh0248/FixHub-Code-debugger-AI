package com.razeef.bugbrother.indexes.service;

import com.razeef.bugbrother.auth.service.CurrentUserService;
import com.razeef.bugbrother.events.IndexRepositoryCommandV2;
import com.razeef.bugbrother.indexes.dto.response.IndexGenerationAllocation;
import com.razeef.bugbrother.indexes.dto.response.IndexTaskSubmissionResult;
import com.razeef.bugbrother.repositories.dto.response.RepositoryResponse;
import com.razeef.bugbrother.tasks.exception.TaskPublicationException;
import com.razeef.bugbrother.tasks.messaging.TaskCommandPublisher;
import com.razeef.bugbrother.tasks.model.TaskEntity;
import com.razeef.bugbrother.tasks.model.TaskType;
import com.razeef.bugbrother.tasks.service.TaskService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class IndexTaskSubmissionService {

    private static final String TOPIC = "code-guardian-index-tasks";

    private final CurrentUserService currentUserService;
    private final IndexGenerationService generationService;
    private final TaskService taskService;
    private final TaskCommandPublisher commandPublisher;

    public IndexTaskSubmissionService(
            CurrentUserService currentUserService,
            IndexGenerationService generationService,
            TaskService taskService,
            TaskCommandPublisher commandPublisher
    ) {
        this.currentUserService = currentUserService;
        this.generationService = generationService;
        this.taskService = taskService;
        this.commandPublisher = commandPublisher;
    }

    public IndexTaskSubmissionResult submitForCurrentUser(
            RepositoryResponse repository,
            String githubToken
    ) {
        return submitForUser(
                currentUserService.requireUserId(),
                repository,
                githubToken
        );
    }

    public IndexTaskSubmissionResult submitForUser(
            String userId,
            RepositoryResponse repository,
            String githubToken
    ) {
        IndexGenerationAllocation generation =
                generationService.allocateForUser(userId, repository);

        TaskEntity task = taskService.createQueuedTaskForUser(
                userId,
                TaskType.INDEX_REPOSITORY,
                repository.repositoryId(),
                repository.owner(),
                repository.name(),
                repository.selectedBranch(),
                repository.commitSha(),
                generation.generationId(),
                null,
                null
        );

        IndexRepositoryCommandV2 command = new IndexRepositoryCommandV2(
                UUID.randomUUID(),
                task.getTaskId(),
                task.getUserId(),
                generation.generationId(),
                repository.repositoryId(),
                repository.owner(),
                repository.name(),
                repository.selectedBranch(),
                repository.commitSha(),
                generation.vectorClientId(),
                generation.modelId(),
                generation.embeddingDimension(),
                generation.chunkerVersion(),
                generation.status().name(),
                generation.buildRequired(),
                githubToken,
                Instant.now()
        );

        try {
            commandPublisher.publish(TOPIC, task.getTaskId(), command);
            return new IndexTaskSubmissionResult(task, true);
        } catch (TaskPublicationException exception) {
            if (generation.buildRequired()) {
                generationService.discardPreparedGeneration(
                        generation.generationId()
                );
            }
            return new IndexTaskSubmissionResult(task, false);
        }
    }
}
