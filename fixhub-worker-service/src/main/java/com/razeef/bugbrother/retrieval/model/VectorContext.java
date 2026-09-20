package com.razeef.bugbrother.retrieval.model;

import java.util.List;
import java.util.UUID;

public record VectorContext(
        UUID generationId,
        String vectorClientId,
        String commitSha,

        int requestedHits,
        int resolvedHits,

        List<RetrievedSourceFile> files
) {
}