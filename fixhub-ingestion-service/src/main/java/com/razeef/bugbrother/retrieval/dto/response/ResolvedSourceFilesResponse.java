package com.razeef.bugbrother.retrieval.dto.response;

import java.util.List;
import java.util.UUID;

public record ResolvedSourceFilesResponse(
        UUID generationId,
        String vectorClientId,
        String commitSha,
        List<ResolvedSourceFileResponse> files
) {
}
