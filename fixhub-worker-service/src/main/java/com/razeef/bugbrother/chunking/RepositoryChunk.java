package com.razeef.bugbrother.chunking;

import java.util.Objects;

public record RepositoryChunk(
        long repositoryId,
        String repositoryFullName,
        String commitSha,

        String path,
        String language,
        String symbol,

        int startLine,
        int endLine,

        String fileContentSha256,
        String chunkContentSha256,

        String chunkId,
        long vectorLabel,
        String chunkerVersion,

        String sourceContent,
        String embeddingText
) {

    public RepositoryChunk {
        Objects.requireNonNull(
                repositoryFullName,
                "repositoryFullName is required"
        );
        Objects.requireNonNull(
                commitSha,
                "commitSha is required"
        );
        Objects.requireNonNull(path, "path is required");
        Objects.requireNonNull(
                language,
                "language is required"
        );
        Objects.requireNonNull(
                fileContentSha256,
                "fileContentSha256 is required"
        );
        Objects.requireNonNull(
                chunkContentSha256,
                "chunkContentSha256 is required"
        );
        Objects.requireNonNull(
                chunkId,
                "chunkId is required"
        );
        Objects.requireNonNull(
                chunkerVersion,
                "chunkerVersion is required"
        );
        Objects.requireNonNull(
                sourceContent,
                "sourceContent is required"
        );
        Objects.requireNonNull(
                embeddingText,
                "embeddingText is required"
        );

        if (repositoryId <= 0) {
            throw new IllegalArgumentException(
                    "repositoryId must be positive"
            );
        }

        if (startLine < 1) {
            throw new IllegalArgumentException(
                    "startLine must be positive"
            );
        }

        if (endLine < startLine) {
            throw new IllegalArgumentException(
                    "endLine cannot be before startLine"
            );
        }

        symbol = symbol == null ? "" : symbol;
    }
}