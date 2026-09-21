package com.razeef.bugbrother.indexes.service;

import com.razeef.bugbrother.indexes.model.IndexGenerationEntity;
import com.razeef.bugbrother.indexes.model.IndexGenerationStatus;
import com.razeef.bugbrother.indexes.repository.ActiveIndexGenerationRepository;
import com.razeef.bugbrother.indexes.repository.IndexGenerationRepository;
import com.razeef.bugbrother.tasks.model.TaskStatus;
import com.razeef.bugbrother.tasks.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RetiredGenerationCleanupService {

    private static final List<TaskStatus> TERMINAL_STATUSES =
            List.of(TaskStatus.COMPLETED, TaskStatus.FAILED);

    private final IndexGenerationRepository generationRepository;
    private final ActiveIndexGenerationRepository activeRepository;
    private final TaskRepository taskRepository;

    public RetiredGenerationCleanupService(
            IndexGenerationRepository generationRepository,
            ActiveIndexGenerationRepository activeRepository,
            TaskRepository taskRepository
    ) {
        this.generationRepository = generationRepository;
        this.activeRepository = activeRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public boolean scheduleIfEligible(UUID generationId, Instant cutoff) {
        IndexGenerationEntity generation = generationRepository
                .findLockedByGenerationId(generationId)
                .orElse(null);

        if (generation == null
                || generation.getStatus() != IndexGenerationStatus.RETIRED
                || generation.getRetiredAt() == null
                || !generation.getRetiredAt().isBefore(cutoff)
                || activeRepository.existsByGenerationId(generationId)
                || taskRepository.existsByGenerationIdAndStatusNotIn(
                        generationId,
                        TERMINAL_STATUSES
                )) {
            return false;
        }

        generation.beginRetiredCleanup();
        return true;
    }
}
