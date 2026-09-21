package com.razeef.bugbrother.tasks.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_command_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskCommandOutboxEntity {

    @Id
    @Column(name = "outbox_id", nullable = false)
    private UUID outboxId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    @Column(name = "command_type", nullable = false, length = 32)
    private String commandType;

    @Column(name = "payload", columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public static TaskCommandOutboxEntity pending(
            UUID taskId,
            String topic,
            String commandType,
            String payload
    ) {
        if (taskId == null || topic == null || topic.isBlank()
                || commandType == null || commandType.isBlank()
                || payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Complete outbox command is required");
        }

        TaskCommandOutboxEntity entry = new TaskCommandOutboxEntity();
        entry.outboxId = UUID.randomUUID();
        entry.taskId = taskId;
        entry.topic = topic;
        entry.commandType = commandType;
        entry.payload = payload;
        entry.createdAt = Instant.now();
        entry.nextAttemptAt = entry.createdAt;
        return entry;
    }

    public void markPublished() {
        publishedAt = Instant.now();
        payload = null;
        lastError = null;
    }

    public void markRetry(Throwable failure) {
        attemptCount++;
        lastError = failure.getClass().getSimpleName();
        long delaySeconds = Math.min(300, 1L << Math.min(attemptCount, 8));
        nextAttemptAt = Instant.now().plusSeconds(delaySeconds);
    }

    public boolean isPublished() {
        return publishedAt != null;
    }
}
