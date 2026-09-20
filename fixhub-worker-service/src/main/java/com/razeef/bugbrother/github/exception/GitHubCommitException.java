package com.razeef.bugbrother.github.exception;

public class GitHubCommitException extends RuntimeException {

    public GitHubCommitException(String message) {
        super(message);
    }

    public GitHubCommitException(String message, Throwable cause) {
        super(message, cause);
    }
}
