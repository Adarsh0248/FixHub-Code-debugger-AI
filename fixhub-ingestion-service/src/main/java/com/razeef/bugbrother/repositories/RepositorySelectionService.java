package com.razeef.bugbrother.repositories;

import com.razeef.bugbrother.services.GitAuthService;
import com.razeef.bugbrother.tasks.CurrentUserService;
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
                        repositoryData.defaultBranch(),
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