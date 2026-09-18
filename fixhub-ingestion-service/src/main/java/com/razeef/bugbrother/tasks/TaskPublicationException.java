package com.razeef.bugbrother.tasks;

public class TaskPublicationException extends RuntimeException {

    public TaskPublicationException(Throwable cause) {
        super(
                "The task was saved but could not be published to the worker queue",
                cause
        );
    }
}