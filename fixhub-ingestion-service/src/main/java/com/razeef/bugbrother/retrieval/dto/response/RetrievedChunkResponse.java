package com.razeef.bugbrother.retrieval.dto.response;

public record RetrievedChunkResponse(
        String chunkId,
        String vectorLabel,

        String path,
        String symbol,

        int startLine,
        int endLine,

        String sourceContent,

        float distance,
        int rank
) {
}