package com.razeef.bugbrother.indexes.dto.response;

import com.razeef.bugbrother.indexes.model.IndexedChunkEntity;

import java.util.UUID;

public record PendingChunkSubmissionResponse(
        String chunkId,
        String vectorLabel,
        String embeddingText,
        UUID submissionEventId
) {
    public static PendingChunkSubmissionResponse from(
            IndexedChunkEntity chunk) {
        return new PendingChunkSubmissionResponse(
                chunk.getChunkId(),
                chunk.getVectorLabel(),
                chunk.getEmbeddingText(),
                chunk.getSubmissionEventId());
    }
}
