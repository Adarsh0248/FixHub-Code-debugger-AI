package com.razeef.bugbrother.debug.controller;

import com.razeef.bugbrother.debug.dto.response.DebugErrorResponse;
import com.razeef.bugbrother.indexes.exception.ActiveIndexGenerationRequiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice(assignableTypes = GitAiDebug.class)
public class DebugExceptionHandler {

    @ExceptionHandler(
            ActiveIndexGenerationRequiredException.class
    )
    public ResponseEntity<DebugErrorResponse>
    handleActiveIndexRequired(
            ActiveIndexGenerationRequiredException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new DebugErrorResponse(
                        exception.getErrorCode(),
                        exception.getMessage(),
                        Instant.now()
                ));
    }
}