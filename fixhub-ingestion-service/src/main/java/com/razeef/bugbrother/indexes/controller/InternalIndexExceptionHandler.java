package com.razeef.bugbrother.indexes.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
        assignableTypes =
                InternalIndexManifestController.class
)
public class InternalIndexExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RepositoryInternalError>
    handleInvalidInput(
            IllegalArgumentException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(new RepositoryInternalError(
                        "INVALID_MANIFEST",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<RepositoryInternalError>
    handleConflict(
            IllegalStateException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new RepositoryInternalError(
                        "MANIFEST_CONFLICT",
                        exception.getMessage()
                ));
    }

    public record RepositoryInternalError(
            String error,
            String message
    ) {
    }
}


