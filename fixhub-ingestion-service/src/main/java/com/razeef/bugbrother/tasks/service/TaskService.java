package com.razeef.bugbrother.tasks.service;

import com.razeef.bugbrother.auth.service.CurrentUserService;
import com.razeef.bugbrother.tasks.dto.response.TaskAcceptedResponse;
import com.razeef.bugbrother.tasks.dto.response.TaskResponse;
import com.razeef.bugbrother.tasks.exception.TaskNotFoundException;
import com.razeef.bugbrother.tasks.model.ProcessedTaskEvent;
import com.razeef.bugbrother.tasks.model.TaskEntity;
import com.razeef.bugbrother.tasks.model.TaskType;
import com.razeef.bugbrother.tasks.repository.ProcessedTaskEventRepository;
import com.razeef.bugbrother.tasks.repository.TaskRepository;

import com.razeef.bugbrother.events.TaskStatusEventV1;
import com.razeef.bugbrother.events.TaskStatusEventV2;
import com.razeef.bugbrother.debug.model.DebugMode;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProcessedTaskEventRepository processedTaskEventRepository;
    private final CurrentUserService currentUserService;

    public TaskService(
            TaskRepository taskRepository,
            ProcessedTaskEventRepository processedTaskEventRepository,
            CurrentUserService currentUserService
    ) {
        this.taskRepository = taskRepository;
        this.processedTaskEventRepository = processedTaskEventRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
        public TaskEntity createQueuedTask(
                TaskType taskType,

                Long repositoryId,
                String owner,
                String repo,
                String branch,
                String baseCommitSha,
                UUID generationId,

                DebugMode debugMode,

                String requestSummary
        ) {
        String userId =
                currentUserService.requireUserId();

        TaskEntity task = TaskEntity.queued(
                taskType,
                userId,

                repositoryId,
                owner,
                repo,
                branch,
                baseCommitSha,
                generationId,

                debugMode,

                requestSummary
        );

        return taskRepository.save(task);
        }

    @Transactional
    public void markPublicationFailed(
            UUID taskId,
            String errorMessage
    ) {
        TaskEntity task = taskRepository.findLockedByTaskId(taskId)
                .orElseThrow(() ->
                        new IllegalStateException("Task not found: " + taskId)
                );

        task.markPublicationFailed(sanitize(errorMessage));
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID taskId) {
        String userId = currentUserService.requireUserId();

        return taskRepository.findByTaskIdAndUserId(taskId, userId)
                .map(TaskResponse::from)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listTasks(int limit) {
        String userId = currentUserService.requireUserId();

        int safeLimit = Math.max(1, Math.min(limit, 100));

        return taskRepository
                .findByUserIdOrderByUpdatedAtDesc(
                        userId,
                        PageRequest.of(0, safeLimit)
                )
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    @KafkaListener(
            topics = "code-guardian-task-events",
            groupId = "code-guardian-status-projection"
    )
    @Transactional
    public void projectStatusEvent(TaskStatusEventV1 event) {
        if (processedTaskEventRepository.existsById(event.eventId())) {
            return;
        }

        TaskEntity task = taskRepository
                .findLockedByTaskId(event.taskId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Received an event for unknown task: "
                                        + event.taskId()
                        )
                );

        task.applyStatusEvent(event);

        processedTaskEventRepository.save(
                new ProcessedTaskEvent(
                        event.eventId(),
                        event.taskId()
                )
        );
    }

    @KafkaListener(
            topics = "code-guardian-task-events-v2",
            groupId = "code-guardian-status-projection-v2"
    )
    @Transactional
    public void projectStatusEventV2(TaskStatusEventV2 event) {
        if (processedTaskEventRepository.existsById(event.eventId())) {
            return;
        }

        TaskEntity task = taskRepository
                .findLockedByTaskId(event.taskId())
                .orElseThrow(() -> new IllegalStateException(
                        "Received an event for unknown task: " + event.taskId()
                ));

        task.applyStatusEvent(event);
        processedTaskEventRepository.save(new ProcessedTaskEvent(
                event.eventId(),
                event.taskId()
        ));
    }

    public TaskAcceptedResponse acceptedResponse(TaskEntity task) {
        return new TaskAcceptedResponse(
                task.getTaskId(),
                task.getStatus(),
                "/api/tasks/" + task.getTaskId()
        );
    }

    private String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown Kafka publication error";
        }

        return message.length() <= 1000
                ? message
                : message.substring(0, 1000);
    }
}
