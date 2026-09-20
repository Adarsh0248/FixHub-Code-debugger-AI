package com.razeef.bugbrother.indexes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "indexed_symbols")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndexedSymbolEntity {

    @Id
    @Column(name = "symbol_id", nullable = false)
    private UUID symbolId;

    @Column(name = "generation_id", nullable = false)
    private UUID generationId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(
            name = "symbol_name",
            nullable = false,
            length = 512
    )
    private String symbolName;

    @Column(
            name = "qualified_name",
            nullable = false,
            length = 1000
    )
    private String qualifiedName;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "symbol_kind",
            nullable = false,
            length = 32
    )
    private IndexedSymbolKind symbolKind;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static IndexedSymbolEntity create(
            UUID generationId,
            UUID fileId,
            String symbolName,
            String qualifiedName,
            IndexedSymbolKind symbolKind
    ) {
        if (generationId == null || fileId == null) {
            throw new IllegalArgumentException(
                    "Generation and file IDs are required"
            );
        }

        if (symbolName == null || symbolName.isBlank()) {
            throw new IllegalArgumentException(
                    "symbolName is required"
            );
        }

        if (qualifiedName == null
                || qualifiedName.isBlank()) {
            throw new IllegalArgumentException(
                    "qualifiedName is required"
            );
        }

        if (symbolKind == null) {
            throw new IllegalArgumentException(
                    "symbolKind is required"
            );
        }

        IndexedSymbolEntity symbol =
                new IndexedSymbolEntity();

        symbol.symbolId = UUID.randomUUID();
        symbol.generationId = generationId;
        symbol.fileId = fileId;
        symbol.symbolName = symbolName;
        symbol.qualifiedName = qualifiedName;
        symbol.symbolKind = symbolKind;
        symbol.createdAt = Instant.now();

        return symbol;
    }
}