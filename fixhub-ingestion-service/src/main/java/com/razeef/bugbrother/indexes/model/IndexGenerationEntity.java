package com.razeef.bugbrother.indexes.model;

import com.razeef.bugbrother.indexes.config.IndexConfiguration;

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
@Table(name = "index_generations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndexGenerationEntity {

    @Id
    @Column(name = "generation_id", nullable = false)
    private UUID generationId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Column(name = "branch", nullable = false, length = 255)
    private String branch;

    @Column(name = "commit_sha", nullable = false, length = 64)
    private String commitSha;

    @Column(
            name = "vector_client_id",
            nullable = false,
            length = 20
    )
    private String vectorClientId;

    @Column(name = "model_id", nullable = false, length = 255)
    private String modelId;

    @Column(name = "embedding_dimension", nullable = false)
    private int embeddingDimension;

    @Column(
            name = "chunker_version",
            nullable = false,
            length = 128
    )
    private String chunkerVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private IndexGenerationStatus status;

    @Column(name = "expected_files", nullable = false)
    private int expectedFiles;

    @Column(name = "expected_chunks", nullable = false)
    private int expectedChunks;

    @Column(name = "indexed_chunks", nullable = false)
    private int indexedChunks;

    @Column(name = "failed_chunks", nullable = false)
    private int failedChunks;

    @Column(name = "failure_code", length = 128)
    private String failureCode;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    public static IndexGenerationEntity building(
            UUID generationId,
            String userId,
            Long repositoryId,
            String branch,
            String commitSha,
            String vectorClientId,
            IndexConfiguration configuration
    ) {
        requireText(userId, "userId");
        requireText(branch, "branch");
        requireText(commitSha, "commitSha");
        requireText(vectorClientId, "vectorClientId");

        if (generationId == null) {
            throw new IllegalArgumentException(
                    "generationId is required"
            );
        }

        if (repositoryId == null || repositoryId <= 0) {
            throw new IllegalArgumentException(
                    "repositoryId must be positive"
            );
        }

        Instant now = Instant.now();

        IndexGenerationEntity generation =
                new IndexGenerationEntity();

        generation.generationId = generationId;
        generation.userId = userId;
        generation.repositoryId = repositoryId;
        generation.branch = branch;
        generation.commitSha = commitSha;
        generation.vectorClientId = vectorClientId;

        generation.modelId = configuration.modelId();
        generation.embeddingDimension =
                configuration.embeddingDimension();
        generation.chunkerVersion =
                configuration.chunkerVersion();

        generation.status = IndexGenerationStatus.BUILDING;

        generation.expectedFiles = 0;
        generation.expectedChunks = 0;
        generation.indexedChunks = 0;
        generation.failedChunks = 0;

        generation.createdAt = now;
        generation.updatedAt = now;

        return generation;
    }

    public void registerManifest(
            int expectedFiles,
            int expectedChunks
    ) {
        requireBuilding();

        if (expectedFiles < 0 || expectedChunks < 0) {
            throw new IllegalArgumentException(
                    "Manifest counts cannot be negative"
            );
        }

        if (this.expectedFiles != 0
                || this.expectedChunks != 0) {
            throw new IllegalStateException(
                    "Manifest was already registered"
            );
        }

        this.expectedFiles = expectedFiles;
        this.expectedChunks = expectedChunks;
        this.updatedAt = Instant.now();
    }

    public boolean recordIndexedChunk() {
        requireBuilding();

        if (indexedChunks + failedChunks
                >= expectedChunks) {
            return false;
        }

        indexedChunks++;
        updatedAt = Instant.now();

        return true;
    }

    public boolean recordFailedChunk() {
        requireBuilding();

        if (indexedChunks + failedChunks
                >= expectedChunks) {
            return false;
        }

        failedChunks++;
        updatedAt = Instant.now();

        return true;
    }

    public void markReady() {
        requireBuilding();

        if (expectedChunks <= 0) {
            throw new IllegalStateException(
                    "A generation with no chunks cannot become ready"
            );
        }

        if (failedChunks > 0) {
            throw new IllegalStateException(
                    "A generation with failed chunks cannot become ready"
            );
        }

        if (indexedChunks != expectedChunks) {
            throw new IllegalStateException(
                    "Not every chunk has been indexed"
            );
        }

        Instant now = Instant.now();

        status = IndexGenerationStatus.READY;
        activatedAt = now;
        updatedAt = now;
    }

    public void markFailed(
            String failureCode,
            String failureMessage
    ) {
        if (status != IndexGenerationStatus.BUILDING) {
            return;
        }

        Instant now = Instant.now();

        status = IndexGenerationStatus.FAILED;
        this.failureCode = sanitize(
                failureCode,
                128,
                "INDEX_GENERATION_FAILED"
        );
        this.failureMessage = sanitize(
                failureMessage,
                1000,
                "Index generation failed"
        );
        failedAt = now;
        updatedAt = now;
    }

    public void retire() {
        if (status == IndexGenerationStatus.RETIRED) {
            return;
        }

        if (status != IndexGenerationStatus.READY) {
            throw new IllegalStateException(
                    "Only a ready generation can be retired"
            );
        }

        Instant now = Instant.now();

        status = IndexGenerationStatus.RETIRED;
        retiredAt = now;
        updatedAt = now;
    }

    public long vectorClientIdAsLong() {
        return Long.parseUnsignedLong(vectorClientId);
    }

    private void requireBuilding() {
        if (status != IndexGenerationStatus.BUILDING) {
            throw new IllegalStateException(
                    "Generation is not building"
            );
        }
    }

    private static void requireText(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " is required"
            );
        }
    }

    private String sanitize(
            String value,
            int maximumLength,
            String fallback
    ) {
        String safe = value == null || value.isBlank()
                ? fallback
                : value;

        return safe.length() <= maximumLength
                ? safe
                : safe.substring(0, maximumLength);
    }
}


