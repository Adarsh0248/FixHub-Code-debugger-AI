package com.razeef.bugbrother.source.exception;

public class RepositorySourceException extends RuntimeException {

    public RepositorySourceException(String message) {
        super(message);
    }

    public RepositorySourceException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}