package com.razeef.bugbrother.indexes.dto.request;

public record CompleteManifestRequest(
        int expectedFiles,
        int expectedChunks
) {
}


