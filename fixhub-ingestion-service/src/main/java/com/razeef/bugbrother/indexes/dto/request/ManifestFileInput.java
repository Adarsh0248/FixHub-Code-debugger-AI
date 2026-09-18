package com.razeef.bugbrother.indexes.dto.request;

public record ManifestFileInput(
        String path,
        String language,
        String gitBlobSha,
        String contentSha256,
        long sizeBytes,
        String content
) {
}


