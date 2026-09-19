package com.razeef.bugbrother.events;

import java.time.Instant;
import java.util.UUID;

public record IndexRepositoryCommandV2(
        UUID eventId,
        UUID taskId,
        String userId,

        UUID generationId,

        Long repositoryId,
        String owner,
        String repo,
        String branch,
        String commitSha,

        String vectorClientId,
        String modelId,
        int embeddingDimension,
        String chunkerVersion,

        String generationStatus,
        boolean buildRequired,

        String githubToken,
        Instant requestedAt
) {

    public long vectorClientIdAsLong() {
        return Long.parseUnsignedLong(vectorClientId);
    }
}