package com.razeef.bugbrother.github.exception;

public class StaleBaseCommitException extends GitHubCommitException {

    public StaleBaseCommitException(String message) {
        super(message);
    }
}
