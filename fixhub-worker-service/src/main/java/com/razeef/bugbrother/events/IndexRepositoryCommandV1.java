package com.razeef.bugbrother.events;

import java.time.Instant;
import java.util.UUID;

public record IndexRepositoryCommandV1(
        UUID eventId,
        UUID taskId,
        String userId,

        Long repositoryId,
        String owner,
        String repo,
        String branch,
        String commitSha,

        String githubToken,
        Instant requestedAt
) {
}