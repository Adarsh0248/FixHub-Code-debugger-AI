package com.razeef.bugbrother.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bugbrother.context")
public record ContextBudgetProperties(
        int maxTotalCharacters,
        int maxSingleFileCharacters,
        int maxPrimaryFiles,
        int maxSupportingFiles,
        int maxExpansionRounds,
        int maxRequestedFiles
) {
    public ContextBudgetProperties {
        if (maxTotalCharacters <= 0) {
            throw new IllegalArgumentException(
                    "maxTotalCharacters must be positive"
            );
        }
        if (maxSingleFileCharacters <= 0
                || maxSingleFileCharacters > maxTotalCharacters) {
            throw new IllegalArgumentException(
                    "maxSingleFileCharacters must be positive "
                            + "and cannot exceed maxTotalCharacters"
            );
        }
        if (maxPrimaryFiles <= 0 || maxSupportingFiles < 0) {
            throw new IllegalArgumentException(
                    "Context file limits are invalid"
            );
        }
        if (maxExpansionRounds < 0 || maxRequestedFiles < 0) {
            throw new IllegalArgumentException(
                    "Context expansion limits cannot be negative"
            );
        }
    }
}
