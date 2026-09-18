package com.razeef.bugbrother.tasks.controller;

import com.razeef.bugbrother.tasks.dto.response.TaskResponse;
import com.razeef.bugbrother.tasks.exception.TaskNotFoundException;
import com.razeef.bugbrother.tasks.service.TaskService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/{taskId}")
    public TaskResponse getTask(@PathVariable UUID taskId) {
        return taskService.getTask(taskId);
    }

    @GetMapping
    public List<TaskResponse> listTasks(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return taskService.listTasks(limit);
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleTaskNotFound(
            TaskNotFoundException exception
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "error", "TASK_NOT_FOUND",
                        "message", exception.getMessage()
                ));
    }
}