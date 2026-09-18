package com.razeef.bugbrother.tasks;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID taskId,
        TaskType taskType,

        String owner,
        String repo,
        String requestSummary,

        TaskStatus status,
        TaskStage stage,
        long eventSequence,

        Integer progressCurrent,
        Integer progressTotal,
        String statusMessage,

        String errorCode,
        String errorMessage,

        String resultBranch,
        String resultCommitSha,
        String resultUrl,

        String validationSummary,

        Instant createdAt,
        Instant updatedAt
) {
    public static TaskResponse from(TaskEntity task) {
        return new TaskResponse(
                task.getTaskId(),
                task.getTaskType(),

                task.getOwner(),
                task.getRepo(),
                task.getRequestSummary(),

                task.getStatus(),
                task.getStage(),
                task.getEventSequence(),

                task.getProgressCurrent(),
                task.getProgressTotal(),
                task.getStatusMessage(),

                task.getErrorCode(),
                task.getErrorMessage(),

                task.getResultBranch(),
                task.getResultCommitSha(),
                task.getResultUrl(),

                task.getValidationSummary(),

                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}