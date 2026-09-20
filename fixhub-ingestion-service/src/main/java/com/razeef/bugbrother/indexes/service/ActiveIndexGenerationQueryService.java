package com.razeef.bugbrother.indexes.service;

import com.razeef.bugbrother.auth.service.CurrentUserService;
import com.razeef.bugbrother.indexes.dto.response.ActiveIndexGenerationResponse;
import com.razeef.bugbrother.indexes.exception.ActiveIndexGenerationRequiredException;
import com.razeef.bugbrother.indexes.model.ActiveIndexGenerationEntity;
import com.razeef.bugbrother.indexes.model.IndexGenerationEntity;
import com.razeef.bugbrother.indexes.model.IndexGenerationStatus;
import com.razeef.bugbrother.indexes.repository.ActiveIndexGenerationRepository;
import com.razeef.bugbrother.indexes.repository.IndexGenerationRepository;
import com.razeef.bugbrother.repositories.dto.response.RepositoryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActiveIndexGenerationQueryService {

    private final CurrentUserService currentUserService;
    private final ActiveIndexGenerationRepository activeRepository;
    private final IndexGenerationRepository generationRepository;

    public ActiveIndexGenerationQueryService(
            CurrentUserService currentUserService,
            ActiveIndexGenerationRepository activeRepository,
            IndexGenerationRepository generationRepository
    ) {
        this.currentUserService = currentUserService;
        this.activeRepository = activeRepository;
        this.generationRepository = generationRepository;
    }

    @Transactional(readOnly = true)
    public ActiveIndexGenerationResponse requireActiveGeneration(
            RepositoryResponse repository
    ) {
        if (repository == null) {
            throw new IllegalArgumentException(
                    "repository is required"
            );
        }

        String userId = currentUserService.requireUserId();

        ActiveIndexGenerationEntity active = activeRepository
                .findByUserIdAndRepositoryIdAndBranch(
                        userId,
                        repository.repositoryId(),
                        repository.selectedBranch()
                )
                .orElseThrow(() ->
                        new ActiveIndexGenerationRequiredException(
                                "INDEX_REQUIRED",
                                "This repository commit is not indexed. "
                                        + "Index the repository before debugging."
                        )
                );

        IndexGenerationEntity generation = generationRepository
                .findById(active.getGenerationId())
                .orElseThrow(() ->
                        new ActiveIndexGenerationRequiredException(
                                "INDEX_REQUIRED",
                                "The active index is unavailable. "
                                        + "Index the repository again."
                        )
                );

        validateOwnership(
                generation,
                repository,
                userId
        );

        if (generation.getStatus()
                != IndexGenerationStatus.READY) {
            throw new ActiveIndexGenerationRequiredException(
                    "INDEX_NOT_READY",
                    "The repository index is not ready. "
                            + "Wait for indexing to complete."
            );
        }

        if (!generation.getCommitSha()
                .equals(repository.commitSha())) {
            throw new ActiveIndexGenerationRequiredException(
                    "INDEX_STALE",
                    "The repository changed after it was indexed. "
                            + "Index the latest commit before debugging."
            );
        }

        return ActiveIndexGenerationResponse.from(generation);
    }

    private void validateOwnership(
            IndexGenerationEntity generation,
            RepositoryResponse repository,
            String userId
    ) {
        boolean matches = generation.getUserId().equals(userId)
                && generation.getRepositoryId()
                        .equals(repository.repositoryId())
                && generation.getBranch()
                        .equals(repository.selectedBranch());

        if (!matches) {
            throw new ActiveIndexGenerationRequiredException(
                    "INDEX_REQUIRED",
                    "A valid active index was not found for this repository."
            );
        }
    }
}