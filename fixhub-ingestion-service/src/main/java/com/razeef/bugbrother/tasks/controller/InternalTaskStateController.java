package com.razeef.bugbrother.tasks.controller;

import com.razeef.bugbrother.tasks.dto.response.InternalTaskExecutionState;
import com.razeef.bugbrother.tasks.service.TaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/tasks")
public class InternalTaskStateController {

    private final TaskService taskService;

    public InternalTaskStateController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/{taskId}/state")
    public InternalTaskExecutionState state(@PathVariable UUID taskId) {
        return taskService.getExecutionState(taskId);
    }
}
