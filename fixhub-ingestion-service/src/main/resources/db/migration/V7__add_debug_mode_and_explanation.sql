ALTER TABLE tasks
    ADD COLUMN debug_mode VARCHAR(32),
    ADD COLUMN result_explanation TEXT;

ALTER TABLE tasks
    ADD CONSTRAINT chk_tasks_debug_mode
    CHECK (
        debug_mode IS NULL
        OR debug_mode IN ('GUIDE_ONLY', 'FIX_AND_COMMIT')
    );
