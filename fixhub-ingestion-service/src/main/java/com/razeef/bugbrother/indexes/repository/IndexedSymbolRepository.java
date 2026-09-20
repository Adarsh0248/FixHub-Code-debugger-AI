package com.razeef.bugbrother.indexes.repository;

import com.razeef.bugbrother.indexes.model.IndexedSymbolEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IndexedSymbolRepository
        extends JpaRepository<IndexedSymbolEntity, UUID> {

    List<IndexedSymbolEntity>
    findByGenerationIdAndSymbolName(
            UUID generationId,
            String symbolName
    );

    void deleteByGenerationId(UUID generationId);
}