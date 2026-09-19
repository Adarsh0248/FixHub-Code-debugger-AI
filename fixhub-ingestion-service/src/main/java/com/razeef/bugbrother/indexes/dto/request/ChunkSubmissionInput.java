package com.razeef.bugbrother.indexes.dto.request;

import java.util.UUID;

public record ChunkSubmissionInput(
        String chunkId,
        UUID submissionEventId
) {
}