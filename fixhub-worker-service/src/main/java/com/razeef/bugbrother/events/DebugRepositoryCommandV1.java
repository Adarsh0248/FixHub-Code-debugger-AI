package com.razeef.bugbrother.events;

import java.time.Instant;
import java.util.UUID;

public record DebugRepositoryCommandV1(
        UUID eventId,
        UUID taskId,
        String userId,

        Long repositoryId,
        String owner,
        String repo,
        String branch,
        String baseCommitSha,

        String errorQuery,
        String githubToken,
        Instant requestedAt
) {
}