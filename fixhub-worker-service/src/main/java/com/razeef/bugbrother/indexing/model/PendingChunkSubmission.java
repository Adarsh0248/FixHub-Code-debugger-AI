package com.razeef.bugbrother.indexing.model;

import java.util.UUID;

public record PendingChunkSubmission(
        String chunkId,
        String vectorLabel,
        String embeddingText,
        UUID submissionEventId
) {
}
