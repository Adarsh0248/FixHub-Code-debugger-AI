package com.razeef.bugbrother.indexing.model;

import com.razeef.bugbrother.chunking.model.RepositoryChunk;

import java.util.Objects;
import java.util.UUID;

public record PreparedChunkSubmission(
        UUID submissionEventId,
        RepositoryChunk chunk
) {

    public PreparedChunkSubmission {
        Objects.requireNonNull(
                submissionEventId,
                "submissionEventId is required"
        );

        Objects.requireNonNull(
                chunk,
                "chunk is required"
        );
    }
}