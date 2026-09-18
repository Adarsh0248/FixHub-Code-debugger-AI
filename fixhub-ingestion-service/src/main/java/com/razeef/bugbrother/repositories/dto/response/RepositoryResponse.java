package com.razeef.bugbrother.repositories.dto.response;

import com.razeef.bugbrother.repositories.model.GitHubBranchData;
import com.razeef.bugbrother.repositories.model.RepositoryEntity;

import java.time.Instant;

public record RepositoryResponse(
        Long repositoryId,
        String owner,
        String name,
        String fullName,
        boolean privateRepository,

        String defaultBranch,
        String selectedBranch,
        String commitSha,

        boolean canPull,
        boolean canPush,
        boolean admin,

        Instant syncedAt
) {

    public static RepositoryResponse from(
            RepositoryEntity repository,
            GitHubBranchData branch
    ) {
        return new RepositoryResponse(
                repository.getGithubRepositoryId(),
                repository.getOwner(),
                repository.getName(),
                repository.getFullName(),
                repository.isPrivateRepository(),

                repository.getDefaultBranch(),
                branch.name(),
                branch.commitSha(),

                repository.isCanPull(),
                repository.isCanPush(),
                repository.isAdmin(),

                repository.getSyncedAt()
        );
    }
}