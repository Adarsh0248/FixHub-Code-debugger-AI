package com.razeef.bugbrother.indexes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "indexed_chunks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndexedChunkEntity {

    @Id
    @Column(name = "chunk_row_id", nullable = false)
    private UUID chunkRowId;

    @Column(name = "generation_id", nullable = false)
    private UUID generationId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "chunk_id", nullable = false, length = 64)
    private String chunkId;

    @Column(
            name = "vector_label",
            nullable = false,
            length = 20
    )
    private String vectorLabel;

    @Column(name = "path", nullable = false, length = 2000)
    private String path;

    @Column(name = "language", nullable = false, length = 64)
    private String language;

    @Column(name = "symbol", nullable = false, length = 512)
    private String symbol;

    @Column(name = "start_line", nullable = false)
    private int startLine;

    @Column(name = "end_line", nullable = false)
    private int endLine;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "file_content_sha256",
            nullable = false,
            length = 64
    )
    private String fileContentSha256;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "chunk_content_sha256",
            nullable = false,
            length = 64
    )
    private String chunkContentSha256;

    @Column(
            name = "chunker_version",
            nullable = false,
            length = 128
    )
    private String chunkerVersion;

    @Column(
            name = "source_content",
            nullable = false,
            columnDefinition = "text"
    )
    private String sourceContent;

    @Column(
            name = "embedding_text",
            nullable = false,
            columnDefinition = "text"
    )
    private String embeddingText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ChunkIndexStatus status;

    @Column(name = "submission_event_id")
    private UUID submissionEventId;

    @Column(name = "error_code", length = 128)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "indexed_at")
    private Instant indexedAt;

    public static IndexedChunkEntity pending(
            UUID generationId,
            UUID fileId,

            String chunkId,
            long vectorLabel,

            String path,
            String language,
            String symbol,

            int startLine,
            int endLine,

            String fileContentSha256,
            String chunkContentSha256,
            String chunkerVersion,

            String sourceContent,
            String embeddingText
    ) {
        if (generationId == null || fileId == null) {
            throw new IllegalArgumentException(
                    "Generation and file IDs are required"
            );
        }

        requireText(chunkId, "chunkId");
        requireText(path, "path");
        requireText(language, "language");
        requireText(
                fileContentSha256,
                "fileContentSha256"
        );
        requireText(
                chunkContentSha256,
                "chunkContentSha256"
        );
        requireText(chunkerVersion, "chunkerVersion");
        requireText(sourceContent, "sourceContent");
        requireText(embeddingText, "embeddingText");

        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException(
                    "Invalid chunk line range"
            );
        }

        Instant now = Instant.now();

        IndexedChunkEntity chunk =
                new IndexedChunkEntity();

        chunk.chunkRowId = UUID.randomUUID();
        chunk.generationId = generationId;
        chunk.fileId = fileId;

        chunk.chunkId = chunkId;
        chunk.vectorLabel =
                Long.toUnsignedString(vectorLabel);

        chunk.path = path;
        chunk.language = language;
        chunk.symbol = symbol == null ? "" : symbol;

        chunk.startLine = startLine;
        chunk.endLine = endLine;

        chunk.fileContentSha256 =
                fileContentSha256;
        chunk.chunkContentSha256 =
                chunkContentSha256;
        chunk.chunkerVersion = chunkerVersion;

        chunk.sourceContent = sourceContent;
        chunk.embeddingText = embeddingText;

        chunk.status = ChunkIndexStatus.PENDING;
        chunk.createdAt = now;
        chunk.updatedAt = now;

        return chunk;
    }

    public boolean markSubmitted(
            UUID submissionEventId
    ) {
        if (submissionEventId == null) {
            throw new IllegalArgumentException(
                    "submissionEventId is required"
            );
        }

        if (status == ChunkIndexStatus.SUBMITTED
                && submissionEventId.equals(
                        this.submissionEventId
                )) {
            return false;
        }

        if (status != ChunkIndexStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a pending chunk can be submitted"
            );
        }

        Instant now = Instant.now();

        status = ChunkIndexStatus.SUBMITTED;
        this.submissionEventId = submissionEventId;
        submittedAt = now;
        updatedAt = now;

        return true;
    }

    public boolean markIndexed() {
        if (status == ChunkIndexStatus.INDEXED) {
            return false;
        }

        if (status != ChunkIndexStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Only a submitted chunk can be indexed"
            );
        }

        Instant now = Instant.now();

        status = ChunkIndexStatus.INDEXED;
        indexedAt = now;
        updatedAt = now;

        return true;
    }

    public boolean markFailed(
            String errorCode,
            String errorMessage
    ) {
        if (status == ChunkIndexStatus.FAILED) {
            return false;
        }

        if (status == ChunkIndexStatus.INDEXED) {
            return false;
        }

        status = ChunkIndexStatus.FAILED;
        this.errorCode = sanitize(
                errorCode,
                128,
                "CHUNK_INDEX_FAILED"
        );
        this.errorMessage = sanitize(
                errorMessage,
                1000,
                "Chunk indexing failed"
        );
        updatedAt = Instant.now();

        return true;
    }

    public long vectorLabelAsLong() {
        return Long.parseUnsignedLong(vectorLabel);
    }

    private static void requireText(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " is required"
            );
        }
    }

    private static String sanitize(
            String value,
            int maximumLength,
            String fallback
    ) {
        String safe = value == null || value.isBlank()
                ? fallback
                : value;

        return safe.length() <= maximumLength
                ? safe
                : safe.substring(0, maximumLength);
    }
}


