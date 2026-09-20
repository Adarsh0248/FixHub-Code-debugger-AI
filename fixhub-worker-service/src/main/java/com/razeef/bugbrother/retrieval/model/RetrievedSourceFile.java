package com.razeef.bugbrother.retrieval.model;

import java.util.List;
import java.util.UUID;

public record RetrievedSourceFile(
        UUID fileId,
        String path,
        String language,

        String gitBlobSha,
        String contentSha256,
        String content,

        float bestDistance,
        int bestRank,

        List<RetrievedChunk> matchedChunks
) {
}