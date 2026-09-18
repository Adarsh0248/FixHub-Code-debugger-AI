package com.razeef.bugbrother.indexes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "active_index_generations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActiveIndexGenerationEntity {

    @Id
    @Column(
            name = "active_generation_id",
            nullable = false
    )
    private UUID activeGenerationId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Column(name = "branch", nullable = false, length = 255)
    private String branch;

    @Column(name = "generation_id", nullable = false)
    private UUID generationId;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;

    public static ActiveIndexGenerationEntity create(
            String userId,
            Long repositoryId,
            String branch,
            UUID generationId
    ) {
        ActiveIndexGenerationEntity active =
                new ActiveIndexGenerationEntity();

        active.activeGenerationId = UUID.randomUUID();
        active.userId = userId;
        active.repositoryId = repositoryId;
        active.branch = branch;
        active.generationId = generationId;
        active.activatedAt = Instant.now();

        return active;
    }

    public void pointTo(
            UUID generationId
    ) {
        if (generationId == null) {
            throw new IllegalArgumentException(
                    "generationId is required"
            );
        }

        this.generationId = generationId;
        this.activatedAt = Instant.now();
    }
}


