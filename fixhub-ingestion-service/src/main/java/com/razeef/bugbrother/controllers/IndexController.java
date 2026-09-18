package com.razeef.bugbrother.controllers;

import com.razeef.bugbrother.events.IndexRepositoryCommandV1;
import com.razeef.bugbrother.repositories.RepositoryResponse;
import com.razeef.bugbrother.repositories.RepositorySelectionService;
import com.razeef.bugbrother.services.GitAuthService;
import com.razeef.bugbrother.tasks.TaskAcceptedResponse;
import com.razeef.bugbrother.tasks.TaskCommandPublisher;
import com.razeef.bugbrother.tasks.TaskEntity;
import com.razeef.bugbrother.tasks.TaskPublicationException;
import com.razeef.bugbrother.tasks.TaskService;
import com.razeef.bugbrother.tasks.TaskStatus;
import com.razeef.bugbrother.tasks.TaskType;
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
    private final TaskService taskService;
    private final TaskCommandPublisher commandPublisher;

    public IndexController(
            GitAuthService gitAuthService,
            RepositorySelectionService repositorySelectionService,
            TaskService taskService,
            TaskCommandPublisher commandPublisher
    ) {
        this.gitAuthService = gitAuthService;
        this.repositorySelectionService =
                repositorySelectionService;
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

        String githubToken =
                gitAuthService.getGitHubAccessToken();

        TaskEntity task = taskService.createQueuedTask(
                TaskType.INDEX_REPOSITORY,

                repository.repositoryId(),
                repository.owner(),
                repository.name(),
                repository.selectedBranch(),
                repository.commitSha(),

                null
        );

        IndexRepositoryCommandV1 command =
                new IndexRepositoryCommandV1(
                        UUID.randomUUID(),
                        task.getTaskId(),
                        task.getUserId(),

                        repository.repositoryId(),
                        repository.owner(),
                        repository.name(),
                        repository.selectedBranch(),
                        repository.commitSha(),

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