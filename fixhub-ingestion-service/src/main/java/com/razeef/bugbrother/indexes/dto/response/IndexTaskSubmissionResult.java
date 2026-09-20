package com.razeef.bugbrother.indexes.dto.response;

import com.razeef.bugbrother.tasks.model.TaskEntity;

public record IndexTaskSubmissionResult(
        TaskEntity task,
        boolean published
) {
}
