package com.razeef.bugbrother.indexes.dto.event;

import java.time.Instant;

public record VectorIndexResultEvent(
        int schemaVersion,
        String correlationId,
        String clientId,
        String vectorLabel,
        String outcome,
        boolean duplicate,
        String errorCode,
        String errorMessage,
        Instant completedAt
) {
}
