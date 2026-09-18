package com.razeef.bugbrother.repositories.controller;

import com.razeef.bugbrother.repositories.dto.response.RepositoryErrorResponse;
import com.razeef.bugbrother.repositories.exception.GitHubApiException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.razeef.bugbrother.debug.controller.GitAiDebug;
import com.razeef.bugbrother.indexes.controller.IndexController;


@RestControllerAdvice(
        assignableTypes = {
                RepositoryController.class,
                IndexController.class,
                GitAiDebug.class
        }
)
public class RepositoryExceptionHandler {

    @ExceptionHandler(GitHubApiException.class)
    public ResponseEntity<RepositoryErrorResponse>
    handleGitHubApiException(
            GitHubApiException exception
    ) {
        HttpStatus status = switch (exception.getError()) {
            case AUTHENTICATION_FAILED ->
                    HttpStatus.UNAUTHORIZED;

            case ACCESS_DENIED ->
                    HttpStatus.FORBIDDEN;

            case NOT_FOUND_OR_INACCESSIBLE ->
                    HttpStatus.NOT_FOUND;

            case RATE_LIMITED ->
                    HttpStatus.TOO_MANY_REQUESTS;

            case UPSTREAM_UNAVAILABLE,
                 UPSTREAM_FAILURE,
                 INVALID_RESPONSE ->
                    HttpStatus.BAD_GATEWAY;
        };

        return ResponseEntity
                .status(status)
                .body(new RepositoryErrorResponse(
                        exception.getError().name(),
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RepositoryErrorResponse>
    handleInvalidInput(
            IllegalArgumentException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(new RepositoryErrorResponse(
                        "INVALID_REPOSITORY_INPUT",
                        exception.getMessage()
                ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<RepositoryErrorResponse>
    handleAuthenticationState(
            IllegalStateException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new RepositoryErrorResponse(
                        "AUTHENTICATION_REQUIRED",
                        exception.getMessage()
                ));
    }
}