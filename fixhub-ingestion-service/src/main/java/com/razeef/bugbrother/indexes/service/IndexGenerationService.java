package com.razeef.bugbrother.indexes.service;

import com.razeef.bugbrother.indexes.config.IndexConfiguration;
import com.razeef.bugbrother.indexes.dto.response.IndexGenerationAllocation;
import com.razeef.bugbrother.indexes.dto.response.IndexGenerationStatusResponse;
import com.razeef.bugbrother.indexes.dto.response.IndexCleanupPlanResponse;
import com.razeef.bugbrother.indexes.repository.ActiveIndexGenerationRepository;
import com.razeef.bugbrother.indexes.repository.IndexedChunkRepository;
import com.razeef.bugbrother.indexes.model.IndexGenerationEntity;
import com.razeef.bugbrother.indexes.model.IndexGenerationStatus;
import com.razeef.bugbrother.indexes.repository.IndexGenerationRepository;

import com.razeef.bugbrother.repositories.dto.response.RepositoryResponse;
import com.razeef.bugbrother.auth.service.CurrentUserService;
import com.razeef.bugbrother.tasks.model.TaskEntity;
import com.razeef.bugbrother.tasks.model.TaskStatus;
import com.razeef.bugbrother.tasks.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class IndexGenerationService {

    private static final List<IndexGenerationStatus>
            REUSABLE_STATUSES = List.of(
                    IndexGenerationStatus.BUILDING,
                    IndexGenerationStatus.READY
            );

    private final IndexGenerationRepository generationRepository;
    private final ActiveIndexGenerationRepository activeRepository;
    private final IndexedChunkRepository chunkRepository;
    private final TaskRepository taskRepository;
    private final IndexConfiguration configuration;
    private final CurrentUserService currentUserService;
    private final SecureRandom secureRandom;

    public IndexGenerationService(
            IndexGenerationRepository generationRepository,
            ActiveIndexGenerationRepository activeRepository,
            IndexedChunkRepository chunkRepository,
            TaskRepository taskRepository,
            IndexConfiguration configuration,
            CurrentUserService currentUserService
    ) {
        this.generationRepository = generationRepository;
        this.activeRepository = activeRepository;
        this.chunkRepository = chunkRepository;
        this.taskRepository = taskRepository;
        this.configuration = configuration;
        this.currentUserService = currentUserService;
        this.secureRandom = new SecureRandom();
    }

    @Transactional
    public IndexGenerationAllocation allocate(
            RepositoryResponse repository
    ) {
        String userId =
                currentUserService.requireUserId();

        Optional<IndexGenerationEntity> reusable =
                generationRepository
                        .findFirstByUserIdAndRepositoryIdAndBranchAndCommitShaAndModelIdAndChunkerVersionAndStatusInOrderByCreatedAtDesc(
                                userId,
                                repository.repositoryId(),
                                repository.selectedBranch(),
                                repository.commitSha(),
                                configuration.modelId(),
                                configuration.chunkerVersion(),
                                REUSABLE_STATUSES
                        );

        if (reusable.isPresent()) {
            return IndexGenerationAllocation.from(
                    reusable.get(),
                    false
            );
        }

        UUID generationId = UUID.randomUUID();

        IndexGenerationEntity generation =
                IndexGenerationEntity.building(
                        generationId,
                        userId,
                        repository.repositoryId(),
                        repository.selectedBranch(),
                        repository.commitSha(),
                        nextVectorClientId(),
                        configuration
                );

        IndexGenerationEntity saved =
                generationRepository.save(generation);

        return IndexGenerationAllocation.from(
                saved,
                true
        );
    }

    @Transactional
    public void failGeneration(
            UUID generationId,
            String errorCode,
            String errorMessage,
            boolean vectorsMayExist
    ) {
        IndexGenerationEntity generation =
                generationRepository
                        .findLockedByGenerationId(generationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Generation not found: "
                                                + generationId
                                )
                        );

        generation.markFailed(
                errorCode,
                errorMessage
        );

        if (vectorsMayExist
                || generation.isVectorSubmissionStarted()) {
            generation.beginCleanup();
        }

        for (TaskEntity task : taskRepository
                .findLockedByGenerationIdAndStatusNotIn(
                        generationId,
                        List.of(
                                TaskStatus.COMPLETED,
                                TaskStatus.FAILED
                        )
                )) {
            task.failIndexGeneration(
                    errorCode,
                    errorMessage
            );
        }
    }

    @Transactional
    public void discardPreparedGeneration(UUID generationId) {
        IndexGenerationEntity generation = generationRepository
                .findLockedByGenerationId(generationId)
                .orElse(null);

        if (generation == null) {
            return;
        }

        if (generation.getStatus() != IndexGenerationStatus.BUILDING
                && generation.getStatus() != IndexGenerationStatus.FAILED) {
            throw new IllegalStateException(
                    "Only an unsubmitted generation can be discarded"
            );
        }

        if (generation.isVectorSubmissionStarted()
                || generation.getIndexedChunks() > 0) {
            throw new IllegalStateException(
                    "A generation with indexed vectors must use cleanup"
            );
        }

        generationRepository.delete(generation);
        generationRepository.flush();
    }

    @Transactional
    public void markVectorSubmissionStarted(UUID generationId) {
        IndexGenerationEntity generation = generationRepository
                .findLockedByGenerationId(generationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Generation not found: " + generationId
                ));

        generation.markVectorSubmissionStarted();
    }

    @Transactional
    public boolean failTimedOutTask(
            UUID taskId,
            Instant cutoff
    ) {
        TaskEntity snapshot = taskRepository.findById(taskId)
                .orElse(null);

        if (snapshot == null
                || snapshot.getStatus().isTerminal()
                || !snapshot.getUpdatedAt().isBefore(cutoff)) {
            return false;
        }

        UUID generationId = snapshot.getGenerationId();
        IndexGenerationEntity generation = generationId == null
                ? null
                : generationRepository
                        .findLockedByGenerationId(generationId)
                        .orElse(null);

        TaskEntity task = taskRepository
                .findLockedByTaskId(taskId)
                .orElse(null);

        if (task == null
                || task.getStatus().isTerminal()
                || !task.getUpdatedAt().isBefore(cutoff)) {
            return false;
        }

        task.markWorkerTimeout();

        if (generation == null
                || generation.getStatus()
                        != IndexGenerationStatus.BUILDING) {
            return true;
        }

        if (taskRepository.existsCurrentTaskForGeneration(
                generationId,
                taskId,
                List.of(TaskStatus.COMPLETED, TaskStatus.FAILED),
                cutoff
        )) {
            return true;
        }

        if (generation.isVectorSubmissionStarted()) {
            generation.markFailed(
                    "WORKER_TIMEOUT",
                    "Worker stopped after vector submission began; cleanup scheduled"
            );
            generation.beginCleanup();
        } else {
            generation.markFailed(
                    "WORKER_TIMEOUT",
                    "Worker stopped before vector submission; retry indexing"
            );
            task.detachIndexGeneration();
            generationRepository.delete(generation);
            generationRepository.flush();
        }

        return true;
    }

    @Transactional(readOnly = true)
    public Optional<IndexCleanupPlanResponse> cleanupPlan(
            UUID generationId
    ) {
        return generationRepository.findById(generationId)
                .filter(generation -> generation.getStatus()
                        == IndexGenerationStatus.CLEANING)
                .map(generation -> new IndexCleanupPlanResponse(
                        generation.getGenerationId(),
                        generation.getVectorClientId(),
                        chunkRepository
                                .findByGenerationIdOrderByPathAscStartLineAsc(
                                        generationId
                                )
                                .stream()
                                .map(chunk -> chunk.getVectorLabel())
                                .toList()
                ));
    }

    @Transactional
    public void completeCleanup(UUID generationId) {
        IndexGenerationEntity generation = generationRepository
                .findLockedByGenerationId(generationId)
                .orElse(null);

        if (generation == null) {
            return;
        }

        if (generation.getStatus() != IndexGenerationStatus.CLEANING) {
            throw new IllegalStateException(
                    "Generation is not awaiting cleanup"
            );
        }

        generationRepository.delete(generation);
        generationRepository.flush();
    }

    @Transactional(readOnly = true)
    public IndexGenerationAllocation get(
            UUID generationId
    ) {
        String userId =
                currentUserService.requireUserId();

        IndexGenerationEntity generation =
                generationRepository
                        .findById(generationId)
                        .filter(candidate ->
                                candidate.getUserId()
                                        .equals(userId)
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Generation not found: "
                                                + generationId
                                )
                        );

        return IndexGenerationAllocation.from(
                generation,
                false
        );
    }

    @Transactional(readOnly = true)
    public IndexGenerationStatusResponse getStatus(
            UUID generationId
    ) {
        String userId = currentUserService.requireUserId();

        IndexGenerationEntity generation = generationRepository
                .findById(generationId)
                .filter(candidate -> candidate
                        .getUserId()
                        .equals(userId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Generation not found: " + generationId
                ));

        boolean active = activeRepository
                .findByUserIdAndRepositoryIdAndBranch(
                        userId,
                        generation.getRepositoryId(),
                        generation.getBranch()
                )
                .map(pointer -> pointer
                        .getGenerationId()
                        .equals(generationId))
                .orElse(false);

        return IndexGenerationStatusResponse.from(
                generation,
                active
        );
    }

    private String nextVectorClientId() {
        long value;

        do {
            value = secureRandom.nextLong();
        } while (value == 0);

        return Long.toUnsignedString(value);
    }
}


