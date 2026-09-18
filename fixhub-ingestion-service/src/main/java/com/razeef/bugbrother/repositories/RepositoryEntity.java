package com.razeef.bugbrother.repositories;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "user_repositories",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_user_repositories_user_github_id",
                        columnNames = {
                                "user_id",
                                "github_repository_id"
                        }
                ),
                @UniqueConstraint(
                        name = "uq_user_repositories_user_full_name",
                        columnNames = {
                                "user_id",
                                "full_name"
                        }
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepositoryEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(
            name = "github_repository_id",
            nullable = false
    )
    private Long githubRepositoryId;

    @Column(name = "owner", nullable = false, length = 255)
    private String owner;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "full_name", nullable = false, length = 512)
    private String fullName;

    @Column(name = "private_repository", nullable = false)
    private boolean privateRepository;

    @Column(name = "default_branch", nullable = false, length = 255)
    private String defaultBranch;

    @Column(name = "can_pull", nullable = false)
    private boolean canPull;

    @Column(name = "can_push", nullable = false)
    private boolean canPush;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    public static RepositoryEntity discovered(
            String userId,
            Long githubRepositoryId,
            String owner,
            String name,
            String fullName,
            boolean privateRepository,
            String defaultBranch,
            boolean canPull,
            boolean canPush,
            boolean admin
    ) {
        RepositoryEntity repository = new RepositoryEntity();

        Instant now = Instant.now();

        repository.id = UUID.randomUUID();
        repository.userId = userId;
        repository.githubRepositoryId = githubRepositoryId;
        repository.owner = owner;
        repository.name = name;
        repository.fullName = fullName;
        repository.privateRepository = privateRepository;
        repository.defaultBranch = defaultBranch;
        repository.canPull = canPull;
        repository.canPush = canPush;
        repository.admin = admin;
        repository.discoveredAt = now;
        repository.syncedAt = now;

        return repository;
    }

    public void refresh(
        Long githubRepositoryId,
        String owner,
        String name,
        String fullName,
        boolean privateRepository,
        String defaultBranch,
        boolean canPull,
        boolean canPush,
        boolean admin
        ) {
        this.githubRepositoryId = githubRepositoryId;
        this.owner = owner;
        this.name = name;
        this.fullName = fullName;
        this.privateRepository = privateRepository;
        this.defaultBranch = defaultBranch;
        this.canPull = canPull;
        this.canPush = canPush;
        this.admin = admin;
        this.syncedAt = Instant.now();
        }
}