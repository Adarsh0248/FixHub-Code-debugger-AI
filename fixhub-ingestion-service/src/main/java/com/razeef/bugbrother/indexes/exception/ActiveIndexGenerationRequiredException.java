package com.razeef.bugbrother.indexes.exception;

public class ActiveIndexGenerationRequiredException
        extends RuntimeException {

    private final String errorCode;

    public ActiveIndexGenerationRequiredException(
            String errorCode,
            String message
    ) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}