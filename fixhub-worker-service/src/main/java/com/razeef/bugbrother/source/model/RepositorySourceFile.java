package com.razeef.bugbrother.source.model;

import java.util.Objects;

public record RepositorySourceFile(
        String path,
        String content,
        String gitBlobSha,
        String contentSha256,
        long sizeBytes
) {

    public RepositorySourceFile {
        Objects.requireNonNull(path, "path is required");
        Objects.requireNonNull(content, "content is required");
        Objects.requireNonNull(
                gitBlobSha,
                "gitBlobSha is required"
        );
        Objects.requireNonNull(
                contentSha256,
                "contentSha256 is required"
        );

        if (path.isBlank()) {
            throw new IllegalArgumentException(
                    "path cannot be blank"
            );
        }

        if (sizeBytes < 0) {
            throw new IllegalArgumentException(
                    "sizeBytes cannot be negative"
            );
        }
    }
}