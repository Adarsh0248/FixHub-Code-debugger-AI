package com.razeef.bugbrother.indexes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
        prefix = "bugbrother.internal"
)
public record InternalApiConfiguration(
        String workerKey
) {

    public InternalApiConfiguration {
        if (workerKey == null
                || workerKey.length() < 32) {
            throw new IllegalArgumentException(
                    "bugbrother.internal.worker-key "
                            + "must contain at least 32 characters"
            );
        }
    }
}


