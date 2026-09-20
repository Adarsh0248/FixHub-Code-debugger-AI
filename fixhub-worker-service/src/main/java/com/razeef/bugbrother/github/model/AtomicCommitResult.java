package com.razeef.bugbrother.github.model;

public record AtomicCommitResult(
        String branchName,
        String commitSha,
        String url
) {
}
