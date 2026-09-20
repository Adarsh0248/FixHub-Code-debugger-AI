package com.razeef.bugbrother.debug.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bugbrother.fix-validation")
public record FixValidationProperties(
        int maxChangedFiles,
        int maxFileCharacters,
        int maxResponseCharacters
) {
    public FixValidationProperties {
        if (maxChangedFiles <= 0
                || maxFileCharacters <= 0
                || maxResponseCharacters <= 0) {
            throw new IllegalArgumentException(
                    "Fix validation limits must be positive"
            );
        }
    }
}
