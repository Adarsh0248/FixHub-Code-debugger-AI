package com.razeef.bugbrother.retrieval.model;

public record RetrievedChunk(
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