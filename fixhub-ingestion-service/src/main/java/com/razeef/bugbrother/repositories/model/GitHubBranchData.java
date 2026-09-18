package com.razeef.bugbrother.repositories.model;

public record GitHubBranchData(
        String name,
        String commitSha,
        boolean protectedBranch
) {
}