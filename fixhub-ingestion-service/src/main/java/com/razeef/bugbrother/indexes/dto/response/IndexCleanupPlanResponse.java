package com.razeef.bugbrother.indexes.dto.response;

import java.util.List;
import java.util.UUID;

public record IndexCleanupPlanResponse(
        UUID generationId,
        String vectorClientId,
        List<String> vectorLabels
) {
}
