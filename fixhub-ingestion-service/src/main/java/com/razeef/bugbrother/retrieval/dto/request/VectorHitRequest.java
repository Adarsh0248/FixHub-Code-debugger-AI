package com.razeef.bugbrother.retrieval.dto.request;

public record VectorHitRequest(
        String vectorLabel,
        float distance,
        int rank
) {
}