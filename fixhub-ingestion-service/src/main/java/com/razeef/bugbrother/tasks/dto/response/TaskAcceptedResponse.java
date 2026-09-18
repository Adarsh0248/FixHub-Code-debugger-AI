package com.razeef.bugbrother.tasks.dto.response;

import com.razeef.bugbrother.tasks.model.TaskStatus;

import java.util.UUID;
public record TaskAcceptedResponse (
   UUID taskId,
   TaskStatus status,
   String statusUrl
){}
