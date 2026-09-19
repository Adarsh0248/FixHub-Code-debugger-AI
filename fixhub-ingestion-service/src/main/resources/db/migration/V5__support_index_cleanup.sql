ALTER TABLE tasks
    DROP CONSTRAINT fk_task_index_generation;

ALTER TABLE tasks
    ADD CONSTRAINT fk_task_index_generation
        FOREIGN KEY (generation_id)
        REFERENCES index_generations(generation_id)
        ON DELETE SET NULL;

ALTER TABLE index_generations
    DROP CONSTRAINT chk_index_generation_status;

ALTER TABLE index_generations
    ADD CONSTRAINT chk_index_generation_status
        CHECK (
            status IN (
                'BUILDING',
                'READY',
                'FAILED',
                'CLEANING',
                'RETIRED'
            )
        );

ALTER TABLE index_generations
    ADD COLUMN vector_submission_started BOOLEAN NOT NULL DEFAULT FALSE;
