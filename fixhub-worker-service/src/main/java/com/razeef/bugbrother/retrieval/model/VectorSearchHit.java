package com.razeef.bugbrother.retrieval.model;

public record VectorSearchHit(
        String vectorLabel,
        float distance,
        int rank
) {
}