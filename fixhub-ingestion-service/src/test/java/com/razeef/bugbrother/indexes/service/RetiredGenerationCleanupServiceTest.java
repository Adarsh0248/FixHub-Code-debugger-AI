package com.razeef.bugbrother.indexes.service;

import com.razeef.bugbrother.indexes.config.IndexConfiguration;
import com.razeef.bugbrother.indexes.model.IndexGenerationEntity;
import com.razeef.bugbrother.indexes.repository.ActiveIndexGenerationRepository;
import com.razeef.bugbrother.indexes.repository.IndexGenerationRepository;
import com.razeef.bugbrother.tasks.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class RetiredGenerationCleanupServiceTest {

    private IndexGenerationRepository generations;
    private ActiveIndexGenerationRepository active;
    private TaskRepository tasks;
    private RetiredGenerationCleanupService service;

    @BeforeEach
    void setUp() {
        generations = mock(IndexGenerationRepository.class);
        active = mock(ActiveIndexGenerationRepository.class);
        tasks = mock(TaskRepository.class);
        service = new RetiredGenerationCleanupService(
                generations,
                active,
                tasks
        );
    }

    @Test
    void keepsRetiredGenerationWhileDebugTaskIsUnfinished() {
        IndexGenerationEntity generation = retiredGeneration();
        UUID id = generation.getGenerationId();
        when(generations.findLockedByGenerationId(id))
                .thenReturn(Optional.of(generation));
        when(tasks.existsByGenerationIdAndStatusNotIn(
                org.mockito.ArgumentMatchers.eq(id),
                anyCollection()
        )).thenReturn(true);

        assertThat(service.scheduleIfEligible(
                id,
                generation.getRetiredAt().plusSeconds(1)
        )).isFalse();
        assertThat(generation.getStatus().name()).isEqualTo("RETIRED");
    }

    @Test
    void movesOldUnusedGenerationIntoExistingCleanupPipeline() {
        IndexGenerationEntity generation = retiredGeneration();
        UUID id = generation.getGenerationId();
        when(generations.findLockedByGenerationId(id))
                .thenReturn(Optional.of(generation));

        assertThat(service.scheduleIfEligible(
                id,
                generation.getRetiredAt().plusSeconds(1)
        )).isTrue();
        assertThat(generation.getStatus().name()).isEqualTo("CLEANING");
    }

    @Test
    void doesNotCleanActiveGenerationEvenIfMarkedRetired() {
        IndexGenerationEntity generation = retiredGeneration();
        UUID id = generation.getGenerationId();
        when(generations.findLockedByGenerationId(id))
                .thenReturn(Optional.of(generation));
        when(active.existsByGenerationId(id)).thenReturn(true);

        assertThat(service.scheduleIfEligible(
                id,
                generation.getRetiredAt().plusSeconds(1)
        )).isFalse();
        assertThat(generation.getStatus().name()).isEqualTo("RETIRED");
        verify(tasks, never()).existsByGenerationIdAndStatusNotIn(
                org.mockito.ArgumentMatchers.eq(id),
                anyCollection()
        );
    }

    private IndexGenerationEntity retiredGeneration() {
        IndexGenerationEntity generation = IndexGenerationEntity.building(
                UUID.randomUUID(),
                "github:123",
                42L,
                "main",
                "a".repeat(40),
                "1234",
                new IndexConfiguration("model", 384, "chunks-v1")
        );
        generation.registerManifest(1, 1);
        generation.recordIndexedChunk();
        generation.markReady();
        generation.retire();
        return generation;
    }
}
