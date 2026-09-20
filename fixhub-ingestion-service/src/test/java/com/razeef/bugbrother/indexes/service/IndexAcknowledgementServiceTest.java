package com.razeef.bugbrother.indexes.service;

import com.razeef.bugbrother.indexes.config.IndexConfiguration;
import com.razeef.bugbrother.indexes.dto.event.VectorIndexResultEvent;
import com.razeef.bugbrother.indexes.model.ActiveIndexGenerationEntity;
import com.razeef.bugbrother.indexes.model.ChunkIndexStatus;
import com.razeef.bugbrother.indexes.model.IndexGenerationEntity;
import com.razeef.bugbrother.indexes.model.IndexGenerationStatus;
import com.razeef.bugbrother.indexes.model.IndexedChunkEntity;
import com.razeef.bugbrother.indexes.model.IndexedSourceFileEntity;
import com.razeef.bugbrother.indexes.repository.ActiveIndexGenerationRepository;
import com.razeef.bugbrother.indexes.repository.IndexGenerationRepository;
import com.razeef.bugbrother.indexes.repository.IndexedChunkRepository;
import com.razeef.bugbrother.repositories.model.RepositoryEntity;
import com.razeef.bugbrother.repositories.repository.RepositoryRepository;
import com.razeef.bugbrother.tasks.model.TaskEntity;
import com.razeef.bugbrother.tasks.model.TaskStatus;
import com.razeef.bugbrother.tasks.model.TaskType;
import com.razeef.bugbrother.tasks.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndexAcknowledgementServiceTest {

    private IndexedChunkRepository chunkRepository;
    private IndexGenerationRepository generationRepository;
    private ActiveIndexGenerationRepository activeRepository;
    private RepositoryRepository repositoryRepository;
    private TaskRepository taskRepository;
    private IndexAcknowledgementService service;

    @BeforeEach
    void setUp() {
        chunkRepository = mock(IndexedChunkRepository.class);
        generationRepository = mock(IndexGenerationRepository.class);
        activeRepository = mock(ActiveIndexGenerationRepository.class);
        repositoryRepository = mock(RepositoryRepository.class);
        taskRepository = mock(TaskRepository.class);

        service = new IndexAcknowledgementService(
                chunkRepository,
                generationRepository,
                activeRepository,
                repositoryRepository,
                taskRepository
        );
    }

    @Test
    void finalSuccessActivatesGenerationAndCompletesTask() {
        Fixture fixture = fixture();

        when(activeRepository.findLocked(
                fixture.userId,
                fixture.repositoryId,
                fixture.branch
        )).thenReturn(Optional.empty());

        service.apply(fixture.result(
                "INDEXED",
                null,
                null
        ));

        assertThat(fixture.chunk.getStatus())
                .isEqualTo(ChunkIndexStatus.INDEXED);
        assertThat(fixture.generation.getStatus())
                .isEqualTo(IndexGenerationStatus.READY);
        assertThat(fixture.generation.getIndexedChunks())
                .isEqualTo(1);
        assertThat(fixture.task.getStatus())
                .isEqualTo(TaskStatus.COMPLETED);

        ArgumentCaptor<ActiveIndexGenerationEntity> active =
                ArgumentCaptor.forClass(
                        ActiveIndexGenerationEntity.class
                );
        verify(activeRepository).save(active.capture());
        assertThat(active.getValue().getGenerationId())
                .isEqualTo(fixture.generationId);
    }

    @Test
    void permanentFailureDoesNotReplaceActiveGeneration() {
        Fixture fixture = fixture();

        service.apply(fixture.result(
                "FAILED",
                "VECTOR_INVALID_ARGUMENT",
                "Invalid embedding dimension"
        ));

        assertThat(fixture.chunk.getStatus())
                .isEqualTo(ChunkIndexStatus.FAILED);
        assertThat(fixture.generation.getStatus())
                .isEqualTo(IndexGenerationStatus.CLEANING);
        assertThat(fixture.generation.getFailedChunks())
                .isEqualTo(1);
        assertThat(fixture.task.getStatus())
                .isEqualTo(TaskStatus.FAILED);
        verify(activeRepository, never()).save(any());
        verify(activeRepository, never()).findLocked(
                any(),
                any(),
                any()
        );
    }

    private Fixture fixture() {
        String userId = "user-1";
        Long repositoryId = 42L;
        String branch = "main";
        UUID generationId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();

        IndexGenerationEntity generation =
                IndexGenerationEntity.building(
                        generationId,
                        userId,
                        repositoryId,
                        branch,
                        "abc123",
                        "18446744073709551614",
                        new IndexConfiguration(
                                "test-model",
                                384,
                                "line-window-v1"
                        )
                );
        generation.registerManifest(1, 1);

        IndexedSourceFileEntity file =
                IndexedSourceFileEntity.create(
                        generationId,
                        "src/Main.java",
                        "java",
                        "blob123",
                        "a".repeat(64),
                        20,
                        "class Main {}"
                );

        IndexedChunkEntity chunk = IndexedChunkEntity.pending(
                generationId,
                file.getFileId(),
                "b".repeat(64),
                -2L,
                "src/Main.java",
                "java",
                "Main",
                1,
                1,
                "a".repeat(64),
                "c".repeat(64),
                "line-window-v1",
                "class Main {}",
                "src/Main.java\nclass Main {}"
        );
        chunk.markSubmitted(submissionId);

        TaskEntity task = TaskEntity.queued(
                TaskType.INDEX_REPOSITORY,
                userId,
                repositoryId,
                "owner",
                "repo",
                branch,
                "abc123",
                generationId,
                null,
                null
        );

        RepositoryEntity repository =
                RepositoryEntity.discovered(
                        userId,
                        repositoryId,
                        "owner",
                        "repo",
                        "owner/repo",
                        false,
                        branch,
                        true,
                        true,
                        true
                );

        when(chunkRepository.findLockedBySubmissionEventId(
                submissionId
        )).thenReturn(Optional.of(chunk));
        when(generationRepository.findLockedByGenerationId(
                generationId
        )).thenReturn(Optional.of(generation));
        when(repositoryRepository
                .findLockedByUserIdAndGithubRepositoryId(
                        userId,
                        repositoryId
                )).thenReturn(Optional.of(repository));
        when(taskRepository
                .findLockedByGenerationIdAndStatusNotIn(
                        any(),
                        any()
                )).thenReturn(List.of(task));

        return new Fixture(
                userId,
                repositoryId,
                branch,
                generationId,
                submissionId,
                generation,
                chunk,
                task
        );
    }

    private record Fixture(
            String userId,
            Long repositoryId,
            String branch,
            UUID generationId,
            UUID submissionId,
            IndexGenerationEntity generation,
            IndexedChunkEntity chunk,
            TaskEntity task
    ) {
        private VectorIndexResultEvent result(
                String outcome,
                String errorCode,
                String errorMessage
        ) {
            return new VectorIndexResultEvent(
                    1,
                    submissionId.toString(),
                    generation.getVectorClientId(),
                    chunk.getVectorLabel(),
                    outcome,
                    false,
                    errorCode,
                    errorMessage,
                    Instant.now()
            );
        }
    }
}
