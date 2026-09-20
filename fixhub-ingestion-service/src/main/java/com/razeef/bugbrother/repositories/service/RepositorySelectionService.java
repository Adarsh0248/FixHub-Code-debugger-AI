package com.razeef.bugbrother.repositories.service;

import com.razeef.bugbrother.repositories.client.GitHubRepositoryClient;
import com.razeef.bugbrother.repositories.dto.response.RepositoryResponse;
import com.razeef.bugbrother.repositories.exception.GitHubApiException;
import com.razeef.bugbrother.repositories.model.GitHubApiError;
import com.razeef.bugbrother.repositories.model.GitHubBranchData;
import com.razeef.bugbrother.repositories.model.GitHubBranchPage;
import com.razeef.bugbrother.repositories.model.GitHubRepositoryData;
import com.razeef.bugbrother.repositories.model.RepositoryEntity;

import com.razeef.bugbrother.auth.service.GitAuthService;
import com.razeef.bugbrother.auth.service.CurrentUserService;
import org.springframework.stereotype.Service;

@Service
public class RepositorySelectionService {

    private final GitAuthService gitAuthService;
    private final CurrentUserService currentUserService;
    private final GitHubRepositoryClient gitHubRepositoryClient;
    private final RepositoryPersistenceService persistenceService;

    public RepositorySelectionService(
            GitAuthService gitAuthService,
            CurrentUserService currentUserService,
            GitHubRepositoryClient gitHubRepositoryClient,
            RepositoryPersistenceService persistenceService
    ) {
        this.gitAuthService = gitAuthService;
        this.currentUserService = currentUserService;
        this.gitHubRepositoryClient = gitHubRepositoryClient;
        this.persistenceService = persistenceService;
    }

    public RepositoryResponse resolveRepository(
            String owner,
            String repo
    ) {
        return resolveRepository(owner, repo, null);
    }

    public RepositoryResponse resolveRepository(
            String owner,
            String repo,
            String requestedBranch
    ) {
        String normalizedOwner = requireValue(
                owner,
                "Repository owner"
        );

        String normalizedRepo = requireValue(
                repo,
                "Repository name"
        );

        String userId =
                currentUserService.requireUserId();

        String accessToken =
                gitAuthService.getGitHubAccessToken();

        GitHubRepositoryData repositoryData =
                gitHubRepositoryClient.getRepository(
                        normalizedOwner,
                        normalizedRepo,
                        accessToken
                );

        if (!repositoryData.canPull()) {
            throw new GitHubApiException(
                    GitHubApiError.ACCESS_DENIED,
                    "The authenticated user cannot read this repository"
            );
        }

        GitHubBranchData branch =
                gitHubRepositoryClient.resolveBranch(
                        repositoryData.owner(),
                        repositoryData.name(),
                        requestedBranch == null
                                || requestedBranch.isBlank()
                                ? repositoryData.defaultBranch()
                                : requestedBranch.trim(),
                        accessToken
                );

        RepositoryEntity repository =
                persistenceService.upsert(
                        userId,
                        repositoryData
                );

        return RepositoryResponse.from(
                repository,
                branch
        );
    }

    public GitHubBranchPage listBranches(
            String owner,
            String repo,
            int page,
            int pageSize
    ) {
        String normalizedOwner = requireValue(owner, "Repository owner");
        String normalizedRepo = requireValue(repo, "Repository name");
        String accessToken = gitAuthService.getGitHubAccessToken();

        GitHubRepositoryData repositoryData =
                gitHubRepositoryClient.getRepository(
                        normalizedOwner,
                        normalizedRepo,
                        accessToken
                );

        if (!repositoryData.canPull()) {
            throw new GitHubApiException(
                    GitHubApiError.ACCESS_DENIED,
                    "The authenticated user cannot read this repository"
            );
        }

        return gitHubRepositoryClient.listBranches(
                repositoryData.owner(),
                repositoryData.name(),
                accessToken,
                page,
                pageSize
        );
    }

    private String requireValue(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required"
            );
        }

        return value.trim();
    }
}
