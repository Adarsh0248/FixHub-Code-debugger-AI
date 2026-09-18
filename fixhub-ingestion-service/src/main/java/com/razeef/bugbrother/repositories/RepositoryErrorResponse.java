package com.razeef.bugbrother.repositories;

public record RepositoryErrorResponse(
        String error,
        String message
) {
}