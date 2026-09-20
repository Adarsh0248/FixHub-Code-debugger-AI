package com.razeef.bugbrother.indexes.repository;

import com.razeef.bugbrother.indexes.model.SourceFileDependencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface SourceFileDependencyRepository
        extends JpaRepository<
                SourceFileDependencyEntity,
                UUID
        > {

    List<SourceFileDependencyEntity>
    findByGenerationIdAndSourceFileId(
            UUID generationId,
            UUID sourceFileId
    );

    List<SourceFileDependencyEntity>
    findByGenerationIdAndSourceFileIdIn(
            UUID generationId,
            Collection<UUID> sourceFileIds
    );

    List<SourceFileDependencyEntity>
    findByGenerationIdAndTargetFileIdIn(
            UUID generationId,
            Collection<UUID> targetFileIds
    );

    void deleteByGenerationId(UUID generationId);
}
