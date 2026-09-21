package com.razeef.bugbrother.tasks.dto.response;

import com.razeef.bugbrother.tasks.model.TaskEntity;

import java.util.UUID;

public record InternalTaskExecutionState(
        UUID taskId,
        boolean terminal,
        long eventSequence
) {
    public static InternalTaskExecutionState from(TaskEntity task) {
        return new InternalTaskExecutionState(task.getTaskId(),
                task.getStatus().isTerminal(), task.getEventSequence());
    }
}
