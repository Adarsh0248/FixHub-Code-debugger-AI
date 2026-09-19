package com.razeef.bugbrother.events;

import java.time.Instant;
import java.util.UUID;

public record CleanupIndexGenerationCommandV1(
        int schemaVersion,
        UUID eventId,
        UUID generationId,
        String vectorClientId,
        Instant requestedAt
) {
    public static CleanupIndexGenerationCommandV1 create(
            UUID generationId,
            String vectorClientId
    ) {
        return new CleanupIndexGenerationCommandV1(
                1,
                UUID.randomUUID(),
                generationId,
                vectorClientId,
                Instant.now()
        );
    }
}
