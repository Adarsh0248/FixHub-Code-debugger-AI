package com.razeef.bugbrother.debug.exception;

public class ModelResponseValidationException extends RuntimeException {

    public ModelResponseValidationException(String message) {
        super(message);
    }

    public ModelResponseValidationException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
