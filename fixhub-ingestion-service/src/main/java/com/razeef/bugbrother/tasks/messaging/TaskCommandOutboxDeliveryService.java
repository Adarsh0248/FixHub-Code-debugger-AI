package com.razeef.bugbrother.tasks.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razeef.bugbrother.events.DebugRepositoryCommandV3;
import com.razeef.bugbrother.events.IndexRepositoryCommandV2;
import com.razeef.bugbrother.tasks.model.TaskCommandOutboxEntity;
import com.razeef.bugbrother.tasks.repository.TaskCommandOutboxRepository;
import com.razeef.bugbrother.tasks.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TaskCommandOutboxDeliveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            TaskCommandOutboxDeliveryService.class);

    private final TaskCommandOutboxRepository outboxRepository;
    private final TaskRepository taskRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Duration publishTimeout;

    public TaskCommandOutboxDeliveryService(
            TaskCommandOutboxRepository outboxRepository,
            TaskRepository taskRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${bugbrother.kafka.publish-timeout:10s}")
            Duration publishTimeout
    ) {
        this.outboxRepository = outboxRepository;
        this.taskRepository = taskRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.publishTimeout = publishTimeout;
    }

    @Transactional
    public void deliver(UUID outboxId) {
        TaskCommandOutboxEntity entry = outboxRepository
                .findLockedById(outboxId).orElse(null);
        if (entry == null || entry.isPublished()
                || entry.getNextAttemptAt().isAfter(Instant.now())) {
            return;
        }

        boolean terminal = taskRepository.findById(entry.getTaskId())
                .map(task -> task.getStatus().isTerminal())
                .orElse(true);
        if (terminal) {
            entry.markPublished();
            return;
        }

        try {
            Object command = switch (entry.getCommandType()) {
                case "INDEX_V2" -> objectMapper.readValue(
                        entry.getPayload(), IndexRepositoryCommandV2.class);
                case "DEBUG_V3" -> objectMapper.readValue(
                        entry.getPayload(), DebugRepositoryCommandV3.class);
                default -> throw new IllegalStateException(
                        "Unsupported outbox command type");
            };

            kafkaTemplate.send(entry.getTopic(),
                    entry.getTaskId().toString(), command)
                    .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
            entry.markPublished();
        } catch (Exception exception) {
            entry.markRetry(exception);
            LOGGER.warn("Task command delivery failed for outbox {} (attempt {})",
                    outboxId, entry.getAttemptCount());
        }
    }
}
