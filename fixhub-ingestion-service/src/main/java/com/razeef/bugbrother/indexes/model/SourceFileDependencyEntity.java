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
@Table(name = "source_file_dependencies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceFileDependencyEntity {

    @Id
    @Column(name = "dependency_id", nullable = false)
    private UUID dependencyId;

    @Column(name = "generation_id", nullable = false)
    private UUID generationId;

    @Column(name = "source_file_id", nullable = false)
    private UUID sourceFileId;

    @Column(name = "target_file_id", nullable = false)
    private UUID targetFileId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "dependency_type",
            nullable = false,
            length = 32
    )
    private SourceDependencyType dependencyType;

    @Column(
            name = "evidence",
            nullable = false,
            length = 1000
    )
    private String evidence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static SourceFileDependencyEntity create(
            UUID generationId,
            UUID sourceFileId,
            UUID targetFileId,
            SourceDependencyType dependencyType,
            String evidence
    ) {
        if (generationId == null
                || sourceFileId == null
                || targetFileId == null) {
            throw new IllegalArgumentException(
                    "Generation, source and target IDs are required"
            );
        }

        if (sourceFileId.equals(targetFileId)) {
            throw new IllegalArgumentException(
                    "A file cannot depend on itself"
            );
        }

        if (dependencyType == null) {
            throw new IllegalArgumentException(
                    "dependencyType is required"
            );
        }

        if (evidence == null || evidence.isBlank()) {
            throw new IllegalArgumentException(
                    "evidence is required"
            );
        }

        SourceFileDependencyEntity dependency =
                new SourceFileDependencyEntity();

        dependency.dependencyId = UUID.randomUUID();
        dependency.generationId = generationId;
        dependency.sourceFileId = sourceFileId;
        dependency.targetFileId = targetFileId;
        dependency.dependencyType = dependencyType;
        dependency.evidence = truncate(evidence);
        dependency.createdAt = Instant.now();

        return dependency;
    }

    private static String truncate(String value) {
        return value.length() <= 1000
                ? value
                : value.substring(0, 1000);
    }
}