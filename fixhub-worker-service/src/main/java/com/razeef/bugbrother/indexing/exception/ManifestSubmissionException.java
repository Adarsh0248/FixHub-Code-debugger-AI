package com.razeef.bugbrother.indexing.exception;

public class ManifestSubmissionException
        extends RuntimeException {

    public ManifestSubmissionException(
            String message
    ) {
        super(message);
    }

    public ManifestSubmissionException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}