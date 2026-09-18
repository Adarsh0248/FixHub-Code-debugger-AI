package com.razeef.bugbrother.repositories.service;

import com.razeef.bugbrother.repositories.model.GitHubRepositoryData;
import com.razeef.bugbrother.repositories.model.RepositoryEntity;
import com.razeef.bugbrother.repositories.repository.RepositoryRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class RepositoryPersistenceService {

    private final RepositoryRepository repositoryRepository;

    public RepositoryPersistenceService(
            RepositoryRepository repositoryRepository
    ) {
        this.repositoryRepository = repositoryRepository;
    }

    @Transactional
    public RepositoryEntity upsert(
            String userId,
            GitHubRepositoryData data
    ) {
        Optional<RepositoryEntity> existing =
                repositoryRepository
                        .findByUserIdAndGithubRepositoryId(
                                userId,
                                data.repositoryId()
                        );

        if (existing.isEmpty()) {
            existing = repositoryRepository
                    .findByUserIdAndOwnerIgnoreCaseAndNameIgnoreCase(
                            userId,
                            data.owner(),
                            data.name()
                    );
        }

        RepositoryEntity repository;

        if (existing.isPresent()) {
            repository = existing.get();

            repository.refresh(
                    data.repositoryId(),
                    data.owner(),
                    data.name(),
                    data.fullName(),
                    data.privateRepository(),
                    data.defaultBranch(),
                    data.canPull(),
                    data.canPush(),
                    data.admin()
            );
        } else {
            repository = RepositoryEntity.discovered(
                    userId,
                    data.repositoryId(),
                    data.owner(),
                    data.name(),
                    data.fullName(),
                    data.privateRepository(),
                    data.defaultBranch(),
                    data.canPull(),
                    data.canPush(),
                    data.admin()
            );
        }

        return repositoryRepository.save(repository);
    }
}