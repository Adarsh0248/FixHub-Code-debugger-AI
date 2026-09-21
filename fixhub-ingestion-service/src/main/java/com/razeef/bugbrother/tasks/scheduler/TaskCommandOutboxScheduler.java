package com.razeef.bugbrother.tasks.scheduler;

import com.razeef.bugbrother.tasks.messaging.TaskCommandOutboxDeliveryService;
import com.razeef.bugbrother.tasks.repository.TaskCommandOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TaskCommandOutboxScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            TaskCommandOutboxScheduler.class);

    private final TaskCommandOutboxRepository repository;
    private final TaskCommandOutboxDeliveryService deliveryService;

    public TaskCommandOutboxScheduler(
            TaskCommandOutboxRepository repository,
            TaskCommandOutboxDeliveryService deliveryService
    ) {
        this.repository = repository;
        this.deliveryService = deliveryService;
    }

    @Scheduled(fixedDelayString =
            "${bugbrother.task.outbox-scan-delay:1000}")
    public void deliverPending() {
        repository.findDueIds(Instant.now(), PageRequest.of(0, 100))
                .forEach(outboxId -> {
                    try {
                        deliveryService.deliver(outboxId);
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Could not process task outbox {}",
                                outboxId, exception);
                    }
                });
    }
}
