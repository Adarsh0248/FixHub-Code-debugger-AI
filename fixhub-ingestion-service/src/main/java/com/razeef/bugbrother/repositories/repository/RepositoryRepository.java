package com.razeef.bugbrother.repositories.repository;

import com.razeef.bugbrother.repositories.model.RepositoryEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositoryRepository
        extends JpaRepository<RepositoryEntity, UUID> {

    List<RepositoryEntity> findByUserIdOrderByFullNameAsc(
            String userId
    );

    Optional<RepositoryEntity>
    findByUserIdAndGithubRepositoryId(
            String userId,
            Long githubRepositoryId
    );

    Optional<RepositoryEntity>
    findByUserIdAndOwnerIgnoreCaseAndNameIgnoreCase(
            String userId,
            String owner,
            String name
    );
}