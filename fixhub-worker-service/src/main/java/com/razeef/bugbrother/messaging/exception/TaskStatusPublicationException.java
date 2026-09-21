package com.razeef.bugbrother.messaging.exception;

public class TaskStatusPublicationException extends RuntimeException {
    public TaskStatusPublicationException(Throwable cause) {
        super("Could not publish task status", cause);
    }
}
