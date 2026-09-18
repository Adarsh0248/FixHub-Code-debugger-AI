package com.razeef.bugbrother.tasks.model;

public enum TaskStage {
    QUEUED,
    STARTING,

    FETCHING,
    RETRIEVING,
    GENERATING,
    COMMITTING,

    COMPLETED,
    FAILED,

    PUBLICATION_FAILED,
    WORKER_TIMEOUT
}