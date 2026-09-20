package com.razeef.bugbrother.events;

import java.time.Instant;
import java.util.UUID;

public record TaskStatusEventV2(
        UUID eventId,
        UUID taskId,
        long sequence,
        String status,
        String stage,
        Integer progressCurrent,
        Integer progressTotal,
        String message,
        String errorCode,
        String errorMessage,
        String resultBranch,
        String resultCommitSha,
        String resultUrl,
        String resultExplanation,
        String validationSummary,
        Instant occurredAt
) {
}
