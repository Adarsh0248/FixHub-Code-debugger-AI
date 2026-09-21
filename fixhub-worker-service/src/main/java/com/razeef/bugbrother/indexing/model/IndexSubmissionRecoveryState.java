package com.razeef.bugbrother.indexing.model;

import java.util.UUID;

public record IndexSubmissionRecoveryState(
        UUID generationId,
        String vectorClientId,
        String status,
        boolean vectorSubmissionStarted,
        int expectedChunks,
        int indexedChunks
) {
}
