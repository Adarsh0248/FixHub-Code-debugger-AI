package com.razeef.bugbrother.indexes.dto.response;

import com.razeef.bugbrother.indexes.model.IndexGenerationEntity;
import com.razeef.bugbrother.indexes.model.IndexGenerationStatus;

import java.util.UUID;

public record IndexSubmissionRecoveryState(
        UUID generationId,
        String vectorClientId,
        IndexGenerationStatus status,
        boolean vectorSubmissionStarted,
        int expectedChunks,
        int indexedChunks
) {
    public static IndexSubmissionRecoveryState from(
            IndexGenerationEntity generation) {
        return new IndexSubmissionRecoveryState(
                generation.getGenerationId(),
                generation.getVectorClientId(),
                generation.getStatus(),
                generation.isVectorSubmissionStarted(),
                generation.getExpectedChunks(),
                generation.getIndexedChunks());
    }
}
