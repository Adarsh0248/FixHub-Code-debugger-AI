package com.razeef.bugbrother.indexes.controller;

import com.razeef.bugbrother.auth.service.GitAuthService;
import com.razeef.bugbrother.indexes.dto.response.IndexTaskSubmissionResult;
import com.razeef.bugbrother.indexes.service.IndexTaskSubmissionService;
import com.razeef.bugbrother.repositories.dto.response.RepositoryResponse;
import com.razeef.bugbrother.repositories.dto.request.BranchSelectionRequest;
import com.razeef.bugbrother.repositories.service.RepositorySelectionService;
import com.razeef.bugbrother.tasks.dto.response.TaskAcceptedResponse;
import com.razeef.bugbrother.tasks.model.TaskStatus;
import com.razeef.bugbrother.tasks.service.TaskService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repos")
public class IndexController {

    private final GitAuthService gitAuthService;
    private final RepositorySelectionService repositorySelectionService;
    private final IndexTaskSubmissionService submissionService;
    private final TaskService taskService;

    public IndexController(
            GitAuthService gitAuthService,
            RepositorySelectionService repositorySelectionService,
            IndexTaskSubmissionService submissionService,
            TaskService taskService
    ) {
        this.gitAuthService = gitAuthService;
        this.repositorySelectionService =
                repositorySelectionService;
        this.submissionService = submissionService;
        this.taskService = taskService;
    }

    @PostMapping("/{owner}/{repo}/index")
    public ResponseEntity<?> index(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestBody BranchSelectionRequest request
    ) {
        if (request == null
                || request.branch() == null
                || request.branch().isBlank()) {
            throw new IllegalArgumentException(
                    "A branch must be selected before indexing"
            );
        }

        RepositoryResponse repository =
                repositorySelectionService.resolveRepository(
                        owner,
                        repo,
                        request.branch()
                );

        String githubToken =
                gitAuthService.getGitHubAccessToken();

        IndexTaskSubmissionResult submission =
                submissionService.submitForCurrentUser(
                        repository,
                        githubToken
                );

        if (!submission.published()) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new TaskAcceptedResponse(
                            submission.task().getTaskId(),
                            TaskStatus.FAILED,
                            "/api/tasks/"
                                    + submission.task().getTaskId()
                    ));
        }

        return ResponseEntity
                .accepted()
                .body(taskService.acceptedResponse(submission.task()));
    }
}
