CREATE TABLE task_command_outbox (
    outbox_id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES tasks(task_id) ON DELETE CASCADE,
    topic VARCHAR(128) NOT NULL,
    command_type VARCHAR(32) NOT NULL,
    payload TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    CONSTRAINT uq_task_command_outbox_task UNIQUE (task_id),
    CONSTRAINT chk_task_command_outbox_attempts CHECK (attempt_count >= 0)
);

CREATE INDEX idx_task_command_outbox_pending
    ON task_command_outbox (next_attempt_at, created_at)
    WHERE published_at IS NULL;
