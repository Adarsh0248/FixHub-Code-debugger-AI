package com.razeef.bugbrother.repositories.dto.response;

public record RepositoryErrorResponse(
        String error,
        String message
) {
}