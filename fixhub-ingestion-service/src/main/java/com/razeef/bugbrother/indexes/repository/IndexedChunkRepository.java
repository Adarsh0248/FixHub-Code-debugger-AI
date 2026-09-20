package com.razeef.bugbrother.indexes.repository;

import com.razeef.bugbrother.indexes.model.ChunkIndexStatus;
import com.razeef.bugbrother.indexes.model.IndexedChunkEntity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface IndexedChunkRepository
        extends JpaRepository<IndexedChunkEntity, UUID> {

    List<IndexedChunkEntity>
    findByGenerationIdOrderByPathAscStartLineAsc(
            UUID generationId
    );

    Optional<IndexedChunkEntity>
    findByGenerationIdAndVectorLabel(
            UUID generationId,
            String vectorLabel
    );

    Optional<IndexedChunkEntity>
    findBySubmissionEventId(
            UUID submissionEventId
    );

    long countByGenerationIdAndStatus(
            UUID generationId,
            ChunkIndexStatus status
    );

    List<IndexedChunkEntity>
        findByGenerationIdAndVectorLabelInAndStatus(
                UUID generationId,
                Collection<String> vectorLabels,
                ChunkIndexStatus status
        );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select chunk
            from IndexedChunkEntity chunk
            where chunk.submissionEventId = :submissionEventId
            """)
    Optional<IndexedChunkEntity>
    findLockedBySubmissionEventId(
            @Param("submissionEventId")
            UUID submissionEventId
    );

    long countByGenerationId(
        UUID generationId
);

Optional<IndexedChunkEntity>
findByGenerationIdAndChunkId(
        UUID generationId,
        String chunkId
);
}


