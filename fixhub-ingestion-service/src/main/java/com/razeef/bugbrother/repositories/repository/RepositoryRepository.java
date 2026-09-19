package com.razeef.bugbrother.repositories.repository;

import com.razeef.bugbrother.repositories.model.RepositoryEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select repository
            from RepositoryEntity repository
            where repository.userId = :userId
              and repository.githubRepositoryId = :repositoryId
            """)
    Optional<RepositoryEntity>
    findLockedByUserIdAndGithubRepositoryId(
            @Param("userId") String userId,
            @Param("repositoryId") Long repositoryId
    );
}
