package com.razeef.bugbrother.retrieval.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
        assignableTypes = InternalVectorContextController.class
)
public class InternalVectorContextExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RetrievalError> handleInvalidRequest(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest().body(
                new RetrievalError(
                        "INVALID_RETRIEVAL_REQUEST",
                        exception.getMessage()
                )
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<RetrievalError> handleConflict(
            IllegalStateException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new RetrievalError(
                        "RETRIEVAL_STATE_CONFLICT",
                        exception.getMessage()
                ));
    }

    public record RetrievalError(
            String errorCode,
            String message
    ) {
    }
}
