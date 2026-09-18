package com.razeef.bugbrother.tasks.service;

import com.razeef.bugbrother.tasks.model.TaskEntity;
import com.razeef.bugbrother.tasks.repository.TaskRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TaskTimeoutService {

    private final TaskRepository taskRepository;

    public TaskTimeoutService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public boolean failIfStale(UUID taskId, Instant cutoff) {
        TaskEntity task = taskRepository
                .findLockedByTaskId(taskId)
                .orElse(null);

        if (task == null
                || task.getStatus().isTerminal()
                || !task.getUpdatedAt().isBefore(cutoff)) {
            return false;
        }

        task.markWorkerTimeout();
        return true;
    }
}