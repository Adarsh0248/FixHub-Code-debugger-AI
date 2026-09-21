package com.razeef.bugbrother.tasks.controller;

import com.razeef.bugbrother.events.TaskStatusEventV2;
import com.razeef.bugbrother.tasks.service.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/tasks")
public class InternalTaskStatusController {

    private final TaskService taskService;

    public InternalTaskStatusController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/status-events")
    public ResponseEntity<Void> apply(@RequestBody TaskStatusEventV2 event) {
        taskService.projectStatusEventV2(event);
        return ResponseEntity.noContent().build();
    }
}
