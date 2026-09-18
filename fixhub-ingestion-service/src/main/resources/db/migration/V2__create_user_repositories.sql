CREATE TABLE user_repositories (
    id UUID PRIMARY KEY,

    user_id VARCHAR(128) NOT NULL,
    github_repository_id BIGINT NOT NULL,

    owner VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    full_name VARCHAR(512) NOT NULL,

    private_repository BOOLEAN NOT NULL,
    default_branch VARCHAR(255) NOT NULL,

    can_pull BOOLEAN NOT NULL,
    can_push BOOLEAN NOT NULL,
    is_admin BOOLEAN NOT NULL,

    discovered_at TIMESTAMPTZ NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_user_repositories_user_github_id
        UNIQUE (user_id, github_repository_id),

    CONSTRAINT uq_user_repositories_user_full_name
        UNIQUE (user_id, full_name)
);

CREATE INDEX idx_user_repositories_user_full_name
    ON user_repositories (user_id, full_name);

CREATE INDEX idx_user_repositories_github_id
    ON user_repositories (github_repository_id);