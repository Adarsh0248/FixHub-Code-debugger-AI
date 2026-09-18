package com.razeef.bugbrother.tasks;

import java.util.UUID;
public record TaskAcceptedResponse (
   UUID taskId,
   TaskStatus status,
   String statusUrl
){}
