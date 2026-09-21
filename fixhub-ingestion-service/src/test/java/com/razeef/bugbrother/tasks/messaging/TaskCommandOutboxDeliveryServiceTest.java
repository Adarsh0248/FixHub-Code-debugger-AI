package com.razeef.bugbrother.tasks.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razeef.bugbrother.events.IndexRepositoryCommandV2;
import com.razeef.bugbrother.tasks.model.TaskCommandOutboxEntity;
import com.razeef.bugbrother.tasks.model.TaskEntity;
import com.razeef.bugbrother.tasks.model.TaskStatus;
import com.razeef.bugbrother.tasks.repository.TaskCommandOutboxRepository;
import com.razeef.bugbrother.tasks.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskCommandOutboxDeliveryServiceTest {

    private TaskCommandOutboxRepository outboxRepository;
    private TaskRepository taskRepository;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private TaskCommandOutboxDeliveryService service;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(TaskCommandOutboxRepository.class);
        taskRepository = mock(TaskRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        service = new TaskCommandOutboxDeliveryService(
                outboxRepository, taskRepository, kafkaTemplate,
                new ObjectMapper().findAndRegisterModules(),
                Duration.ofSeconds(1));
    }

    @Test
    void retainsPayloadAfterPublishFailureThenClearsItOnSuccess() {
        UUID taskId = UUID.randomUUID();
        TaskCommandOutboxEntity entry = entry(taskId);
        TaskEntity task = mock(TaskEntity.class);
        when(task.getStatus()).thenReturn(TaskStatus.QUEUED);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(outboxRepository.findLockedById(entry.getOutboxId()))
                .thenReturn(Optional.of(entry));
        when(kafkaTemplate.send(eq(entry.getTopic()), eq(taskId.toString()),
                any())).thenReturn(CompletableFuture.failedFuture(
                        new IllegalStateException("Kafka unavailable")))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.deliver(entry.getOutboxId());
        assertEquals(1, entry.getAttemptCount());
        assertNotNull(entry.getPayload());

        org.springframework.test.util.ReflectionTestUtils.setField(
                entry, "nextAttemptAt", Instant.now().minusSeconds(1));
        service.deliver(entry.getOutboxId());

        assertNotNull(entry.getPublishedAt());
        assertNull(entry.getPayload());
        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, org.mockito.Mockito.times(2)).send(
                eq(entry.getTopic()), eq(taskId.toString()),
                payloads.capture());
        assertEquals(IndexRepositoryCommandV2.class,
                payloads.getValue().getClass());
    }

    @Test
    void doesNotPublishCommandForTerminalTask() {
        UUID taskId = UUID.randomUUID();
        TaskCommandOutboxEntity entry = entry(taskId);
        TaskEntity task = mock(TaskEntity.class);
        when(task.getStatus()).thenReturn(TaskStatus.FAILED);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(outboxRepository.findLockedById(entry.getOutboxId()))
                .thenReturn(Optional.of(entry));

        service.deliver(entry.getOutboxId());

        assertNotNull(entry.getPublishedAt());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    private TaskCommandOutboxEntity entry(UUID taskId) {
        IndexRepositoryCommandV2 command = new IndexRepositoryCommandV2(
                UUID.randomUUID(), taskId, "user", UUID.randomUUID(),
                1L, "owner", "repo", "main", "commit", "42", "model",
                384, "line-window-v2", "BUILDING", true, "token",
                Instant.now());
        try {
            return TaskCommandOutboxEntity.pending(taskId,
                    "code-guardian-index-tasks", "INDEX_V2",
                    new ObjectMapper().findAndRegisterModules()
                            .writeValueAsString(command));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
