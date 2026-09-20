package com.razeef.bugbrother.retrieval.dto.response;

import java.util.UUID;

public record ResolvedSourceFileResponse(
        UUID fileId,
        String path,
        String language,
        String gitBlobSha,
        String contentSha256,
        String content
) {
}
