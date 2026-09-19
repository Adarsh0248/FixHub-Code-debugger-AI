package com.razeef.bugbrother.indexes.dto.response;

import com.razeef.bugbrother.indexes.model.IndexGenerationEntity;
import com.razeef.bugbrother.indexes.model.IndexGenerationStatus;

import java.time.Instant;
import java.util.UUID;

public record IndexGenerationStatusResponse(
        UUID generationId,
        Long repositoryId,
        String branch,
        String commitSha,
        String vectorClientId,
        String modelId,
        int embeddingDimension,
        String chunkerVersion,
        IndexGenerationStatus status,
        int expectedFiles,
        int expectedChunks,
        int indexedChunks,
        int failedChunks,
        String failureCode,
        String failureMessage,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt,
        Instant failedAt,
        Instant retiredAt
) {

    public static IndexGenerationStatusResponse from(
            IndexGenerationEntity generation,
            boolean active
    ) {
        return new IndexGenerationStatusResponse(
                generation.getGenerationId(),
                generation.getRepositoryId(),
                generation.getBranch(),
                generation.getCommitSha(),
                generation.getVectorClientId(),
                generation.getModelId(),
                generation.getEmbeddingDimension(),
                generation.getChunkerVersion(),
                generation.getStatus(),
                generation.getExpectedFiles(),
                generation.getExpectedChunks(),
                generation.getIndexedChunks(),
                generation.getFailedChunks(),
                generation.getFailureCode(),
                generation.getFailureMessage(),
                active,
                generation.getCreatedAt(),
                generation.getUpdatedAt(),
                generation.getActivatedAt(),
                generation.getFailedAt(),
                generation.getRetiredAt()
        );
    }
}
