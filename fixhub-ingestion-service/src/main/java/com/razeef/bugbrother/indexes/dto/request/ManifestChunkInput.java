package com.razeef.bugbrother.indexes.dto.request;

public record ManifestChunkInput(
        String filePath,

        String chunkId,
        String vectorLabel,

        String path,
        String language,
        String symbol,

        int startLine,
        int endLine,

        String fileContentSha256,
        String chunkContentSha256,
        String chunkerVersion,

        String sourceContent,
        String embeddingText
) {

    public long vectorLabelAsLong() {
        return Long.parseUnsignedLong(vectorLabel);
    }
}


