package com.razeef.bugbrother.controllers;

import com.razeef.bugbrother.events.DebugRepositoryCommandV1;
import com.razeef.bugbrother.models.ResponsePayload;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
public class GitAiDebug {

    private static final String TOPIC =
            "code-guardian-tasks";

    private final GitAuthService gitAuthService;
    private final RepositorySelectionService repositorySelectionService;
    private final TaskService taskService;
    private final TaskCommandPublisher commandPublisher;

    public GitAiDebug(
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

    @PostMapping("/debug/{owner}/{repo}")
    public ResponseEntity<?> debug(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestBody ResponsePayload payload
    ) {
        if (payload == null
                || payload.getUserQ() == null
                || payload.getUserQ().isBlank()) {
            return ResponseEntity
                    .badRequest()
                    .body(
                            "Invalid payload: userQ is required"
                    );
        }

        String errorQuery =
                payload.getUserQ().trim();

        RepositoryResponse repository =
                repositorySelectionService.resolveRepository(
                        owner,
                        repo
                );

        String githubToken =
                gitAuthService.getGitHubAccessToken();

        TaskEntity task = taskService.createQueuedTask(
                TaskType.DEBUG_REPOSITORY,

                repository.repositoryId(),
                repository.owner(),
                repository.name(),
                repository.selectedBranch(),
                repository.commitSha(),

                errorQuery
        );

        DebugRepositoryCommandV1 command =
                new DebugRepositoryCommandV1(
                        UUID.randomUUID(),
                        task.getTaskId(),
                        task.getUserId(),

                        repository.repositoryId(),
                        repository.owner(),
                        repository.name(),
                        repository.selectedBranch(),
                        repository.commitSha(),

                        errorQuery,
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

    @GetMapping("/home")
    public ResponseEntity<String> home() {
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(
                        authentication.getName()
                )) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Authentication required");
        }

        return ResponseEntity.ok(
                "Welcome "
                        + authentication.getName()
                        + " to Git AI Debug Service"
        );
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok(
                "Git AI Debug Ingestion Service is running"
        );
    }
}