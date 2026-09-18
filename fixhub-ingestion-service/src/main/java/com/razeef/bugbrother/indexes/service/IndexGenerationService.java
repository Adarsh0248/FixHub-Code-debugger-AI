package com.razeef.bugbrother.indexes.service;

import com.razeef.bugbrother.indexes.config.IndexConfiguration;
import com.razeef.bugbrother.indexes.dto.response.IndexGenerationAllocation;
import com.razeef.bugbrother.indexes.model.IndexGenerationEntity;
import com.razeef.bugbrother.indexes.model.IndexGenerationStatus;
import com.razeef.bugbrother.indexes.repository.IndexGenerationRepository;

import com.razeef.bugbrother.repositories.dto.response.RepositoryResponse;
import com.razeef.bugbrother.auth.service.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
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
    private final IndexConfiguration configuration;
    private final CurrentUserService currentUserService;
    private final SecureRandom secureRandom;

    public IndexGenerationService(
            IndexGenerationRepository generationRepository,
            IndexConfiguration configuration,
            CurrentUserService currentUserService
    ) {
        this.generationRepository = generationRepository;
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
            String errorMessage
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

    private String nextVectorClientId() {
        long value;

        do {
            value = secureRandom.nextLong();
        } while (value == 0);

        return Long.toUnsignedString(value);
    }
}


