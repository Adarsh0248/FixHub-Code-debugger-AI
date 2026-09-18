package com.razeef.bugbrother.repositories;

public record GitHubRepositoryData(
        Long repositoryId,
        String owner,
        String name,
        String fullName,
        boolean privateRepository,
        String defaultBranch,
        boolean canPull,
        boolean canPush,
        boolean admin
) {
}