package com.razeef.bugbrother.events;

import com.razeef.bugbrother.debug.model.DebugMode;

import java.time.Instant;
import java.util.UUID;

public record DebugRepositoryCommandV3(
        UUID eventId,
        UUID taskId,
        String userId,

        DebugMode mode,

        UUID generationId,
        String vectorClientId,
        String modelId,
        int embeddingDimension,
        String chunkerVersion,

        Long repositoryId,
        String owner,
        String repo,
        String branch,
        String baseCommitSha,

        String errorQuery,
        String githubToken,
        Instant requestedAt
) {
    public long vectorClientIdAsLong() {
        return Long.parseUnsignedLong(vectorClientId);
    }
}
