package com.razeef.bugbrother.indexes.dto.response;

import com.razeef.bugbrother.indexes.model.IndexGenerationEntity;

import java.util.UUID;

public record ActiveIndexGenerationResponse(
        UUID generationId,
        Long repositoryId,
        String branch,
        String commitSha,

        String vectorClientId,
        String modelId,
        int embeddingDimension,
        String chunkerVersion
) {

    public static ActiveIndexGenerationResponse from(
            IndexGenerationEntity generation
    ) {
        return new ActiveIndexGenerationResponse(
                generation.getGenerationId(),
                generation.getRepositoryId(),
                generation.getBranch(),
                generation.getCommitSha(),

                generation.getVectorClientId(),
                generation.getModelId(),
                generation.getEmbeddingDimension(),
                generation.getChunkerVersion()
        );
    }
}