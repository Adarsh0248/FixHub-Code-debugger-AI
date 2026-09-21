package com.razeef.bugbrother.indexes.repository;

import com.razeef.bugbrother.indexes.model.ActiveIndexGenerationEntity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ActiveIndexGenerationRepository
        extends JpaRepository<
                ActiveIndexGenerationEntity,
                UUID
        > {

    boolean existsByGenerationId(UUID generationId);

    Optional<ActiveIndexGenerationEntity>
    findByUserIdAndRepositoryIdAndBranch(
            String userId,
            Long repositoryId,
            String branch
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select active
            from ActiveIndexGenerationEntity active
            where active.userId = :userId
              and active.repositoryId = :repositoryId
              and active.branch = :branch
            """)
    Optional<ActiveIndexGenerationEntity>
    findLocked(
            @Param("userId") String userId,
            @Param("repositoryId") Long repositoryId,
            @Param("branch") String branch
    );
}


