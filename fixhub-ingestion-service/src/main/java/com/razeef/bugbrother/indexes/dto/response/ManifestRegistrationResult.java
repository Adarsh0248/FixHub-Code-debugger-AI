package com.razeef.bugbrother.indexes.dto.response;

import java.util.UUID;

public record ManifestRegistrationResult(
        UUID generationId,
        int expectedFiles,
        int expectedChunks,
        int insertedFiles,
        int insertedChunks,
        boolean complete
) {
}


