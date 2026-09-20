package com.razeef.bugbrother.tasks.dto.response;

import com.razeef.bugbrother.tasks.model.TaskEntity;
import com.razeef.bugbrother.tasks.model.TaskStage;
import com.razeef.bugbrother.tasks.model.TaskStatus;
import com.razeef.bugbrother.tasks.model.TaskType;
import com.razeef.bugbrother.debug.model.DebugMode;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID taskId,
        TaskType taskType,

        Long repositoryId,
        String owner,
        String repo,
        String branch,
        String baseCommitSha,
        UUID generationId,

        DebugMode debugMode,

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

        String resultExplanation,

        String validationSummary,

        Instant createdAt,
        Instant updatedAt
) {

    public static TaskResponse from(
            TaskEntity task
    ) {
        return new TaskResponse(
                task.getTaskId(),
                task.getTaskType(),

                task.getRepositoryId(),
                task.getOwner(),
                task.getRepo(),
                task.getBranch(),
                task.getBaseCommitSha(),
                task.getGenerationId(),
                task.getDebugMode(),
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

                task.getResultExplanation(),

                task.getValidationSummary(),

                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
