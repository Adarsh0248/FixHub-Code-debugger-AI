package com.razeef.bugbrother.indexing.model;

import java.util.List;
import java.util.UUID;

public record IndexCleanupPlan(
        UUID generationId,
        String vectorClientId,
        List<String> vectorLabels
) {
}
