package com.razeef.bugbrother.indexing.service;

import com.razeef.bugbrother.chunking.service.DeterministicRepositoryChunker;
import com.razeef.bugbrother.events.IndexRepositoryCommandV2;
import com.razeef.bugbrother.indexing.client.ManifestSubmissionClient;
import com.razeef.bugbrother.indexing.model.IndexSubmissionRecoveryState;
import com.razeef.bugbrother.indexing.model.PendingChunkSubmission;
import com.razeef.bugbrother.indexing.model.TaskExecutionState;
import com.razeef.bugbrother.messaging.service.TaskStatusPublisher;
import com.razeef.bugbrother.messaging.exception.TaskStatusPublicationException;
import com.razeef.bugbrother.source.service.GitHubRevisionSourceService;
import com.razeef.bugbrother.vector.service.VectorSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class IndexWorkerServiceTest {

    private GitHubRevisionSourceService sourceService;
    private ManifestSubmissionClient manifestClient;
    private VectorSearchService vectorSearchService;
    private TaskStatusPublisher statusPublisher;
    private IndexWorkerService worker;

    @BeforeEach
    void setUp() {
        sourceService = mock(GitHubRevisionSourceService.class);
        manifestClient = mock(ManifestSubmissionClient.class);
        vectorSearchService = mock(VectorSearchService.class);
        statusPublisher = mock(TaskStatusPublisher.class);
        worker = new IndexWorkerService(sourceService,
                mock(DeterministicRepositoryChunker.class),
                manifestClient, vectorSearchService,
                statusPublisher);
    }

    @Test
    void resumesOnlyStoredUnconfirmedChunksWithoutRefetchingGitHub() {
        IndexRepositoryCommandV2 command = command();
        UUID submissionId = UUID.randomUUID();
        PendingChunkSubmission pending = new PendingChunkSubmission(
                "chunk-a", "7", "embedding text", submissionId);
        when(manifestClient.fetchTaskState(command.taskId())).thenReturn(
                new TaskExecutionState(command.taskId(), false, 3));
        when(manifestClient.fetchRecoveryState(command.generationId()))
                .thenReturn(new IndexSubmissionRecoveryState(
                        command.generationId(), "42", "BUILDING",
                        true, 2, 1));
        when(manifestClient.fetchPendingSubmissions(
                command.generationId(), "")).thenReturn(List.of(pending));
        when(manifestClient.fetchPendingSubmissions(
                command.generationId(), "chunk-a")).thenReturn(List.of());

        worker.consumeIndexTask(command);

        verify(vectorSearchService).submitStoredChunk("42", pending);
        verify(sourceService, never()).fetchRevision(
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void ignoresRedeliveryAfterTaskHasCompleted() {
        IndexRepositoryCommandV2 command = command();
        when(manifestClient.fetchTaskState(command.taskId())).thenReturn(
                new TaskExecutionState(command.taskId(), true, 8));

        worker.consumeIndexTask(command);

        verify(manifestClient, never()).fetchRecoveryState(any());
        verify(vectorSearchService, never()).submitStoredChunk(
                anyString(), any());
    }

    @Test
    void doesNotDiscardGenerationWhenStatusDeliveryFails() {
        IndexRepositoryCommandV2 command = command();
        when(manifestClient.fetchTaskState(command.taskId())).thenReturn(
                new TaskExecutionState(command.taskId(), false, 0));
        doThrow(new TaskStatusPublicationException(
                new IllegalStateException("status unavailable")))
                .when(statusPublisher).running(command.taskId(), 1,
                        "STARTING", "Worker accepted the index task");

        assertThrows(TaskStatusPublicationException.class,
                () -> worker.consumeIndexTask(command));

        verify(manifestClient, never()).discardPreparedGeneration(any());
        verify(manifestClient, never()).failGeneration(
                any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void retriesRecoveryApiOutageWithoutCleaningVectors() {
        IndexRepositoryCommandV2 command = command();
        when(manifestClient.fetchTaskState(command.taskId())).thenReturn(
                new TaskExecutionState(command.taskId(), false, 3));
        when(manifestClient.fetchRecoveryState(command.generationId()))
                .thenReturn(new IndexSubmissionRecoveryState(
                        command.generationId(), "42", "BUILDING",
                        true, 2, 1));
        when(manifestClient.fetchPendingSubmissions(
                command.generationId(), "")).thenThrow(
                        WebClientResponseException.create(503,
                                "Unavailable", null, null, null));

        assertThrows(WebClientResponseException.class,
                () -> worker.consumeIndexTask(command));

        verify(manifestClient, never()).failGeneration(
                any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    private IndexRepositoryCommandV2 command() {
        return new IndexRepositoryCommandV2(UUID.randomUUID(),
                UUID.randomUUID(), "user", UUID.randomUUID(), 1L,
                "owner", "repo", "main", "commit", "42", "model",
                384, DeterministicRepositoryChunker.CHUNKER_VERSION,
                "BUILDING", true, "token", Instant.now());
    }
}
