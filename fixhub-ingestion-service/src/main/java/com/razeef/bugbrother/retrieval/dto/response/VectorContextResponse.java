package com.razeef.bugbrother.retrieval.dto.response;

import java.util.List;
import java.util.UUID;

public record VectorContextResponse(
        UUID generationId,
        String vectorClientId,
        String commitSha,

        int requestedHits,
        int resolvedHits,

        List<RetrievedSourceFileResponse> files
) {
}