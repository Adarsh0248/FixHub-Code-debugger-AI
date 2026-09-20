package com.razeef.bugbrother.debug.model;

import com.razeef.bugbrother.retrieval.model.ContextBundle;

public record ModelGenerationResult(
        StructuredDebugResponse response,
        ContextBundle contextBundle,
        int rounds
) {
}
