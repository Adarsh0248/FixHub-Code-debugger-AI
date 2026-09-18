CREATE TABLE index_generations (
    generation_id UUID PRIMARY KEY,

    user_id VARCHAR(128) NOT NULL,
    repository_id BIGINT NOT NULL,

    branch VARCHAR(255) NOT NULL,
    commit_sha VARCHAR(64) NOT NULL,

    -- Decimal text representation of an unsigned 64-bit gateway client ID.
    vector_client_id VARCHAR(20) NOT NULL,

    model_id VARCHAR(255) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    chunker_version VARCHAR(128) NOT NULL,

    status VARCHAR(32) NOT NULL,

    expected_files INTEGER NOT NULL DEFAULT 0,
    expected_chunks INTEGER NOT NULL DEFAULT 0,
    indexed_chunks INTEGER NOT NULL DEFAULT 0,
    failed_chunks INTEGER NOT NULL DEFAULT 0,

    failure_code VARCHAR(128),
    failure_message VARCHAR(1000),

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,

    CONSTRAINT fk_index_generation_user_repository
        FOREIGN KEY (user_id, repository_id)
        REFERENCES user_repositories (
            user_id,
            github_repository_id
        ),

    CONSTRAINT uq_index_generation_revision
        UNIQUE (
            user_id,
            repository_id,
            branch,
            commit_sha,
            model_id,
            chunker_version
        ),

    CONSTRAINT uq_index_generation_vector_client
        UNIQUE (vector_client_id),

    CONSTRAINT chk_index_generation_status
        CHECK (
            status IN (
                'BUILDING',
                'READY',
                'FAILED',
                'RETIRED'
            )
        ),

    CONSTRAINT chk_index_generation_dimension
        CHECK (embedding_dimension > 0),

    CONSTRAINT chk_index_generation_counts
        CHECK (
            expected_files >= 0
            AND expected_chunks >= 0
            AND indexed_chunks >= 0
            AND failed_chunks >= 0
            AND indexed_chunks + failed_chunks
                    <= expected_chunks
        ),

    CONSTRAINT chk_vector_client_id
        CHECK (
            vector_client_id ~ '^[0-9]{1,20}$'
        )
);

CREATE INDEX idx_index_generations_repository_revision
    ON index_generations (
        user_id,
        repository_id,
        branch,
        commit_sha
    );

CREATE INDEX idx_index_generations_status_updated
    ON index_generations (
        status,
        updated_at
    );


CREATE TABLE indexed_source_files (
    file_id UUID PRIMARY KEY,

    generation_id UUID NOT NULL,

    path VARCHAR(2000) NOT NULL,
    language VARCHAR(64) NOT NULL,

    git_blob_sha VARCHAR(64) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,

    size_bytes BIGINT NOT NULL,
    content TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_indexed_source_file_generation
        FOREIGN KEY (generation_id)
        REFERENCES index_generations(generation_id)
        ON DELETE CASCADE,

    CONSTRAINT uq_indexed_source_file_path
        UNIQUE (generation_id, path),

    CONSTRAINT uq_indexed_source_file_identity
        UNIQUE (generation_id, file_id),

    CONSTRAINT chk_indexed_source_file_size
        CHECK (size_bytes >= 0),

    CONSTRAINT chk_indexed_source_file_hash
        CHECK (
            content_sha256 ~ '^[0-9a-f]{64}$'
        )
);

CREATE INDEX idx_indexed_source_files_generation
    ON indexed_source_files (
        generation_id,
        path
    );

CREATE INDEX idx_indexed_source_files_content_hash
    ON indexed_source_files (
        generation_id,
        content_sha256
    );


CREATE TABLE indexed_chunks (
    chunk_row_id UUID PRIMARY KEY,

    generation_id UUID NOT NULL,
    file_id UUID NOT NULL,

    chunk_id CHAR(64) NOT NULL,
    vector_label VARCHAR(20) NOT NULL,

    path VARCHAR(2000) NOT NULL,
    language VARCHAR(64) NOT NULL,
    symbol VARCHAR(512) NOT NULL DEFAULT '',

    start_line INTEGER NOT NULL,
    end_line INTEGER NOT NULL,

    file_content_sha256 CHAR(64) NOT NULL,
    chunk_content_sha256 CHAR(64) NOT NULL,

    chunker_version VARCHAR(128) NOT NULL,

    source_content TEXT NOT NULL,
    embedding_text TEXT NOT NULL,

    status VARCHAR(32) NOT NULL,

    submission_event_id UUID,

    error_code VARCHAR(128),
    error_message VARCHAR(1000),

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    indexed_at TIMESTAMPTZ,

    CONSTRAINT fk_indexed_chunk_generation
        FOREIGN KEY (generation_id)
        REFERENCES index_generations(generation_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_indexed_chunk_file
        FOREIGN KEY (generation_id, file_id)
        REFERENCES indexed_source_files (
            generation_id,
            file_id
        )
        ON DELETE CASCADE,

    CONSTRAINT uq_indexed_chunk_id
        UNIQUE (generation_id, chunk_id),

    CONSTRAINT uq_indexed_chunk_vector_label
        UNIQUE (generation_id, vector_label),

    CONSTRAINT uq_indexed_chunk_submission_event
        UNIQUE (submission_event_id),

    CONSTRAINT chk_indexed_chunk_status
        CHECK (
            status IN (
                'PENDING',
                'SUBMITTED',
                'INDEXED',
                'FAILED'
            )
        ),

    CONSTRAINT chk_indexed_chunk_lines
        CHECK (
            start_line >= 1
            AND end_line >= start_line
        ),

    CONSTRAINT chk_indexed_chunk_id
        CHECK (
            chunk_id ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT chk_indexed_chunk_file_hash
        CHECK (
            file_content_sha256 ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT chk_indexed_chunk_content_hash
        CHECK (
            chunk_content_sha256 ~ '^[0-9a-f]{64}$'
        ),

    CONSTRAINT chk_indexed_chunk_vector_label
        CHECK (
            vector_label ~ '^[0-9]{1,20}$'
        )
);

CREATE INDEX idx_indexed_chunks_generation_status
    ON indexed_chunks (
        generation_id,
        status
    );

CREATE INDEX idx_indexed_chunks_generation_path
    ON indexed_chunks (
        generation_id,
        path,
        start_line
    );

CREATE INDEX idx_indexed_chunks_generation_symbol
    ON indexed_chunks (
        generation_id,
        symbol
    );


CREATE TABLE active_index_generations (
    active_generation_id UUID PRIMARY KEY,

    user_id VARCHAR(128) NOT NULL,
    repository_id BIGINT NOT NULL,
    branch VARCHAR(255) NOT NULL,

    generation_id UUID NOT NULL,
    activated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_active_generation_user_repository
        FOREIGN KEY (user_id, repository_id)
        REFERENCES user_repositories (
            user_id,
            github_repository_id
        ),

    CONSTRAINT fk_active_generation_generation
        FOREIGN KEY (generation_id)
        REFERENCES index_generations(generation_id),

    CONSTRAINT uq_active_generation_repository_branch
        UNIQUE (
            user_id,
            repository_id,
            branch
        ),

    CONSTRAINT uq_active_generation_generation
        UNIQUE (generation_id)
);

CREATE INDEX idx_active_generations_lookup
    ON active_index_generations (
        user_id,
        repository_id,
        branch
    );


ALTER TABLE tasks
    ADD COLUMN generation_id UUID;

ALTER TABLE tasks
    ADD CONSTRAINT fk_task_index_generation
        FOREIGN KEY (generation_id)
        REFERENCES index_generations(generation_id);

CREATE INDEX idx_tasks_generation
    ON tasks (generation_id);