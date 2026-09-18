package com.razeef.bugbrother.indexes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
        prefix = "bugbrother.index"
)
public record IndexConfiguration(
        String modelId,
        int embeddingDimension,
        String chunkerVersion
) {

    public IndexConfiguration {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException(
                    "bugbrother.index.model-id is required"
            );
        }

        if (embeddingDimension <= 0) {
            throw new IllegalArgumentException(
                    "bugbrother.index.embedding-dimension "
                            + "must be positive"
            );
        }

        if (chunkerVersion == null
                || chunkerVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "bugbrother.index.chunker-version is required"
            );
        }
    }
}


