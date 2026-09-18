ALTER TABLE index_generations
    DROP CONSTRAINT uq_index_generation_revision;

CREATE UNIQUE INDEX uq_index_generation_active_revision
    ON index_generations (
        user_id,
        repository_id,
        branch,
        commit_sha,
        model_id,
        chunker_version
    )
    WHERE status IN ('BUILDING', 'READY');