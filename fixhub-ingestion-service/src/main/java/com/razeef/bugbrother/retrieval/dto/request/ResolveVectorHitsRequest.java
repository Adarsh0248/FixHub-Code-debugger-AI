package com.razeef.bugbrother.retrieval.dto.request;

import java.util.List;

public record ResolveVectorHitsRequest(
        String vectorClientId,
        List<VectorHitRequest> hits
) {
} 