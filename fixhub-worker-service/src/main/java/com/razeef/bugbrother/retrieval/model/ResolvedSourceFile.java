package com.razeef.bugbrother.retrieval.model;

import java.util.UUID;

public record ResolvedSourceFile(
        UUID fileId,
        String path,
        String language,
        String gitBlobSha,
        String contentSha256,
        String content
) {
}
