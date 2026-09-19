package com.razeef.bugbrother.tasks.model;

public enum TaskStage {
    QUEUED,
    STARTING,

    FETCHING,
    CHUNKING,
    STORING_MANIFEST,
    SUBMITTING,
    WAITING_FOR_INDEX,

    RETRIEVING,
    GENERATING,
    COMMITTING,

    COMPLETED,
    FAILED,

    PUBLICATION_FAILED,
    WORKER_TIMEOUT
}