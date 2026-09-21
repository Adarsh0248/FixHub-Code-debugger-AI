package com.razeef.bugbrother.indexing.model;

import java.util.UUID;

public record TaskExecutionState(
        UUID taskId,
        boolean terminal,
        long eventSequence
) {
}
