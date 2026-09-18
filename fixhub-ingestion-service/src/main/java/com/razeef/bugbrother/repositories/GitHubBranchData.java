package com.razeef.bugbrother.repositories;

public record GitHubBranchData(
        String name,
        String commitSha,
        boolean protectedBranch
) {
}