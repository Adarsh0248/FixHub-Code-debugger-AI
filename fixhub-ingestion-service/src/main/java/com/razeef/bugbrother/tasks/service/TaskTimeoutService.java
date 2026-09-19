package com.razeef.bugbrother.tasks.service;

import com.razeef.bugbrother.indexes.service.IndexGenerationService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TaskTimeoutService {

    private final IndexGenerationService generationService;

    public TaskTimeoutService(
            IndexGenerationService generationService
    ) {
        this.generationService = generationService;
    }

    @Transactional
    public boolean failIfStale(UUID taskId, Instant cutoff) {
        return generationService.failTimedOutTask(taskId, cutoff);
    }
}
