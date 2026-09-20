package com.razeef.bugbrother.debug.dto.response;

import java.time.Instant;

public record DebugErrorResponse(
        String errorCode,
        String message,
        Instant occurredAt
) {
}