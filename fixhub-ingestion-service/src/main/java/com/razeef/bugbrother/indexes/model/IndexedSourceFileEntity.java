package com.razeef.bugbrother.indexes.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "indexed_source_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndexedSourceFileEntity {

    @Id
    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "generation_id", nullable = false)
    private UUID generationId;

    @Column(name = "path", nullable = false, length = 2000)
    private String path;

    @Column(name = "language", nullable = false, length = 64)
    private String language;

    @Column(name = "git_blob_sha", nullable = false, length = 64)
    private String gitBlobSha;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "content_sha256",
            nullable = false,
            length = 64
    )
    private String contentSha256;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(
            name = "content",
            nullable = false,
            columnDefinition = "text"
    )
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static IndexedSourceFileEntity create(
            UUID generationId,
            String path,
            String language,
            String gitBlobSha,
            String contentSha256,
            long sizeBytes,
            String content
    ) {
        if (generationId == null) {
            throw new IllegalArgumentException(
                    "generationId is required"
            );
        }

        requireText(path, "path");
        requireText(language, "language");
        requireText(gitBlobSha, "gitBlobSha");
        requireText(contentSha256, "contentSha256");

        if (content == null) {
            throw new IllegalArgumentException(
                    "content is required"
            );
        }

        if (sizeBytes < 0) {
            throw new IllegalArgumentException(
                    "sizeBytes cannot be negative"
            );
        }

        IndexedSourceFileEntity file =
                new IndexedSourceFileEntity();

        file.fileId = UUID.randomUUID();
        file.generationId = generationId;
        file.path = path;
        file.language = language;
        file.gitBlobSha = gitBlobSha;
        file.contentSha256 = contentSha256;
        file.sizeBytes = sizeBytes;
        file.content = content;
        file.createdAt = Instant.now();

        return file;
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
}


