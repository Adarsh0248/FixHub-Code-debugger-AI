CREATE TABLE indexed_symbols (
    symbol_id UUID PRIMARY KEY,

    generation_id UUID NOT NULL,
    file_id UUID NOT NULL,

    symbol_name VARCHAR(512) NOT NULL,
    qualified_name VARCHAR(1000) NOT NULL,
    symbol_kind VARCHAR(32) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_indexed_symbol_file
        FOREIGN KEY (generation_id, file_id)
        REFERENCES indexed_source_files (
            generation_id,
            file_id
        )
        ON DELETE CASCADE,

    CONSTRAINT uq_indexed_symbol_qualified_name
        UNIQUE (
            generation_id,
            qualified_name
        ),

    CONSTRAINT chk_indexed_symbol_kind
        CHECK (
            symbol_kind IN (
                'CLASS',
                'INTERFACE',
                'ENUM',
                'RECORD'
            )
        )
);

CREATE INDEX idx_indexed_symbols_generation_name
    ON indexed_symbols (
        generation_id,
        symbol_name
    );

CREATE INDEX idx_indexed_symbols_generation_qualified
    ON indexed_symbols (
        generation_id,
        qualified_name
    );


CREATE TABLE source_file_dependencies (
    dependency_id UUID PRIMARY KEY,

    generation_id UUID NOT NULL,
    source_file_id UUID NOT NULL,
    target_file_id UUID NOT NULL,

    dependency_type VARCHAR(32) NOT NULL,
    evidence VARCHAR(1000) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_dependency_source_file
        FOREIGN KEY (
            generation_id,
            source_file_id
        )
        REFERENCES indexed_source_files (
            generation_id,
            file_id
        )
        ON DELETE CASCADE,

    CONSTRAINT fk_dependency_target_file
        FOREIGN KEY (
            generation_id,
            target_file_id
        )
        REFERENCES indexed_source_files (
            generation_id,
            file_id
        )
        ON DELETE CASCADE,

    CONSTRAINT uq_source_file_dependency
        UNIQUE (
            generation_id,
            source_file_id,
            target_file_id,
            dependency_type
        ),

    CONSTRAINT chk_dependency_not_self
        CHECK (
            source_file_id <> target_file_id
        ),

    CONSTRAINT chk_dependency_type
        CHECK (
            dependency_type IN (
                'IMPORT',
                'SYMBOL_REFERENCE'
            )
        )
);

CREATE INDEX idx_dependencies_source
    ON source_file_dependencies (
        generation_id,
        source_file_id
    );

CREATE INDEX idx_dependencies_target
    ON source_file_dependencies (
        generation_id,
        target_file_id
    );