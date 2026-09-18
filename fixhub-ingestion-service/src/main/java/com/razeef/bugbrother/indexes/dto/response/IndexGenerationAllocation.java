package com.razeef.bugbrother.indexes.dto.response;

import com.razeef.bugbrother.indexes.model.IndexGenerationEntity;
import com.razeef.bugbrother.indexes.model.IndexGenerationStatus;

import java.util.UUID;

public record IndexGenerationAllocation(
        UUID generationId,
        Long repositoryId,
        String branch,
        String commitSha,

        String vectorClientId,
        String modelId,
        int embeddingDimension,
        String chunkerVersion,

        IndexGenerationStatus status,
        boolean buildRequired
) {

    public long vectorClientIdAsLong() {
        return Long.parseUnsignedLong(vectorClientId);
    }

    public static IndexGenerationAllocation from(
            IndexGenerationEntity generation,
            boolean buildRequired
    ) {
        return new IndexGenerationAllocation(
                generation.getGenerationId(),
                generation.getRepositoryId(),
                generation.getBranch(),
                generation.getCommitSha(),

                generation.getVectorClientId(),
                generation.getModelId(),
                generation.getEmbeddingDimension(),
                generation.getChunkerVersion(),

                generation.getStatus(),
                buildRequired
        );
    }
}


