package com.razeef.bugbrother.indexes.repository;

import com.razeef.bugbrother.indexes.model.IndexGenerationEntity;
import com.razeef.bugbrother.indexes.model.IndexGenerationStatus;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.List;

public interface IndexGenerationRepository
        extends JpaRepository<IndexGenerationEntity, UUID> {

    

    Optional<IndexGenerationEntity>
    findFirstByUserIdAndRepositoryIdAndBranchAndCommitShaAndModelIdAndChunkerVersionAndStatusInOrderByCreatedAtDesc(
        String userId,
        Long repositoryId,
        String branch,
        String commitSha,
        String modelId,
        String chunkerVersion,
        Collection<IndexGenerationStatus> statuses
);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select generation
            from IndexGenerationEntity generation
            where generation.generationId = :generationId
            """)
    Optional<IndexGenerationEntity> findLockedByGenerationId(
            @Param("generationId") UUID generationId
    );

    List<IndexGenerationEntity> findByStatusOrderByUpdatedAtAsc(
            IndexGenerationStatus status
    );
}


