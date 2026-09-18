package com.razeef.bugbrother.tasks;

public enum TaskStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}