package com.razeef.bugbrother.indexes.controller;

import com.razeef.bugbrother.auth.service.GitAuthService;
import com.razeef.bugbrother.events.IndexRepositoryCommandV2;
import com.razeef.bugbrother.indexes.dto.response.IndexGenerationAllocation;
import com.razeef.bugbrother.indexes.service.IndexGenerationService;
import com.razeef.bugbrother.repositories.dto.response.RepositoryResponse;
import com.razeef.bugbrother.repositories.service.RepositorySelectionService;
import com.razeef.bugbrother.tasks.dto.response.TaskAcceptedResponse;
import com.razeef.bugbrother.tasks.exception.TaskPublicationException;
import com.razeef.bugbrother.tasks.messaging.TaskCommandPublisher;
import com.razeef.bugbrother.tasks.model.TaskEntity;
import com.razeef.bugbrother.tasks.model.TaskStatus;
import com.razeef.bugbrother.tasks.model.TaskType;
import com.razeef.bugbrother.tasks.service.TaskService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/repos")
public class IndexController {

    private static final String TOPIC =
            "code-guardian-index-tasks";

    private final GitAuthService gitAuthService;
    private final RepositorySelectionService repositorySelectionService;
    private final IndexGenerationService generationService;
    private final TaskService taskService;
    private final TaskCommandPublisher commandPublisher;

    public IndexController(
            GitAuthService gitAuthService,
            RepositorySelectionService repositorySelectionService,
            IndexGenerationService generationService,
            TaskService taskService,
            TaskCommandPublisher commandPublisher
    ) {
        this.gitAuthService = gitAuthService;
        this.repositorySelectionService =
                repositorySelectionService;
        this.generationService = generationService;
        this.taskService = taskService;
        this.commandPublisher = commandPublisher;
    }

    @PostMapping("/{owner}/{repo}/index")
    public ResponseEntity<?> index(
            @PathVariable String owner,
            @PathVariable String repo
    ) {
        RepositoryResponse repository =
                repositorySelectionService.resolveRepository(
                        owner,
                        repo
                );

        IndexGenerationAllocation generation =
                generationService.allocate(repository);

        String githubToken =
                gitAuthService.getGitHubAccessToken();

        TaskEntity task = taskService.createQueuedTask(
                TaskType.INDEX_REPOSITORY,

                repository.repositoryId(),
                repository.owner(),
                repository.name(),
                repository.selectedBranch(),
                repository.commitSha(),
                generation.generationId(),

                null
        );

        IndexRepositoryCommandV2 command =
                new IndexRepositoryCommandV2(
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
            commandPublisher.publish(
                    TOPIC,
                    task.getTaskId(),
                    command
            );
        } catch (TaskPublicationException exception) {
            if (generation.buildRequired()) {
                generationService.discardPreparedGeneration(
                        generation.generationId()
                );
            }

            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new TaskAcceptedResponse(
                            task.getTaskId(),
                            TaskStatus.FAILED,
                            "/api/tasks/" + task.getTaskId()
                    ));
        }

        return ResponseEntity
                .accepted()
                .body(taskService.acceptedResponse(task));
    }
}
