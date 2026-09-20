package com.razeef.bugbrother.indexes.repository;

import com.razeef.bugbrother.indexes.model.IndexedSourceFileEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IndexedSourceFileRepository
        extends JpaRepository<IndexedSourceFileEntity, UUID> {

    List<IndexedSourceFileEntity>
    findByGenerationIdOrderByPathAsc(
            UUID generationId
    );
    
    List<IndexedSourceFileEntity>
        findByGenerationIdAndFileIdIn(
                UUID generationId,
                Collection<UUID> fileIds
        );


    Optional<IndexedSourceFileEntity>
    findByGenerationIdAndPath(
            UUID generationId,
            String path
    );
    
    long countByGenerationId(
        UUID generationId
    );
}


