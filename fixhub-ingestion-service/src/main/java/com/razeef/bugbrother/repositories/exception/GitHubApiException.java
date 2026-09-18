package com.razeef.bugbrother.repositories.exception;

import com.razeef.bugbrother.repositories.model.GitHubApiError;

import lombok.Getter;

@Getter
public class GitHubApiException extends RuntimeException {

    private final GitHubApiError error;

    public GitHubApiException(
            GitHubApiError error,
            String message
    ) {
        super(message);
        this.error = error;
    }

    public GitHubApiException(
            GitHubApiError error,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.error = error;
    }
}