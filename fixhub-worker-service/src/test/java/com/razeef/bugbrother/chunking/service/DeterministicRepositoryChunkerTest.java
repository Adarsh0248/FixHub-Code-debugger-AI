package com.razeef.bugbrother.chunking.service;

import com.razeef.bugbrother.chunking.model.RepositoryChunk;
import com.razeef.bugbrother.source.model.RepositorySourceFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicRepositoryChunkerTest {

    @Test
    void unchangedSourceReusesEmbeddingTextAcrossCommits() {
        DeterministicRepositoryChunker chunker =
                new DeterministicRepositoryChunker();
        RepositorySourceFile source = new RepositorySourceFile(
                "src/Main.java",
                "class Main {}",
                "blob-sha",
                "content-sha",
                13
        );

        RepositoryChunk first = chunker.chunkRevision(
                42L,
                "owner/repo",
                "a".repeat(40),
                List.of(source)
        ).getFirst();
        RepositoryChunk second = chunker.chunkRevision(
                42L,
                "owner/repo",
                "b".repeat(40),
                List.of(source)
        ).getFirst();

        assertThat(first.embeddingText())
                .isEqualTo(second.embeddingText());
        assertThat(first.chunkId()).isNotEqualTo(second.chunkId());
        assertThat(first.chunkerVersion()).isEqualTo("line-window-v2");
    }
}
