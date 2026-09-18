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
@Table(name = "processed_task_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedTaskEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedTaskEvent(UUID eventId, UUID taskId) {
        this.eventId = eventId;
        this.taskId = taskId;
        this.processedAt = Instant.now();
    }
}