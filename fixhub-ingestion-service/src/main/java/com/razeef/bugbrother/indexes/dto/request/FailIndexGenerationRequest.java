package com.razeef.bugbrother.indexes.dto.request;

public record FailIndexGenerationRequest(
        String errorCode,
        String errorMessage,
        boolean vectorsMayExist
) {
}
