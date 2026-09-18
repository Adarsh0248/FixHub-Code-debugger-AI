CREATE TABLE tasks (
    task_id UUID PRIMARY KEY,

    task_type VARCHAR(32) NOT NULL,
    user_id VARCHAR(128) NOT NULL,

    repository_id BIGINT,
    owner VARCHAR(255) NOT NULL,
    repo VARCHAR(255) NOT NULL,
    branch VARCHAR(255),
    base_commit_sha VARCHAR(64),

    request_summary TEXT,

    status VARCHAR(32) NOT NULL,
    stage VARCHAR(64) NOT NULL,
    event_sequence BIGINT NOT NULL DEFAULT 0,

    progress_current INTEGER,
    progress_total INTEGER,

    status_message VARCHAR(1000),

    error_code VARCHAR(128),
    error_message VARCHAR(1000),

    result_branch VARCHAR(512),
    result_commit_sha VARCHAR(64),
    result_url VARCHAR(1000),

    validation_summary VARCHAR(2000),

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_tasks_user_updated
    ON tasks (user_id, updated_at DESC);

CREATE INDEX idx_tasks_user_repository_updated
    ON tasks (user_id, repository_id, updated_at DESC);

CREATE INDEX idx_tasks_active_updated
    ON tasks (updated_at)
    WHERE status NOT IN ('COMPLETED', 'FAILED');


CREATE TABLE processed_task_events (
    event_id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_processed_task_event_task
        FOREIGN KEY (task_id)
        REFERENCES tasks(task_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_processed_task_events_task
    ON processed_task_events (task_id);