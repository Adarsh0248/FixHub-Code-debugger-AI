package com.razeef.bugbrother.dependencies.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
        assignableTypes = InternalDependencyGraphController.class
)
public class InternalDependencyGraphExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<GraphError> handleInvalidRequest(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest().body(
                new GraphError(
                        "INVALID_GRAPH_REQUEST",
                        exception.getMessage()
                )
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<GraphError> handleConflict(
            IllegalStateException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new GraphError(
                        "GRAPH_STATE_CONFLICT",
                        exception.getMessage()
                ));
    }

    public record GraphError(
            String errorCode,
            String message
    ) {
    }
}
