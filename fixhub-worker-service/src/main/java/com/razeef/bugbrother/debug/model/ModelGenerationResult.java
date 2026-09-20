package com.razeef.bugbrother.debug.model;

import com.razeef.bugbrother.retrieval.model.ContextBundle;

public record ModelGenerationResult(
        String response,
        ContextBundle contextBundle,
        int rounds
) {
}
