package com.razeef.bugbrother.indexes.scheduler;

import com.razeef.bugbrother.events.CleanupIndexGenerationCommandV1;
import com.razeef.bugbrother.indexes.messaging.IndexCleanupCommandPublisher;
import com.razeef.bugbrother.indexes.model.IndexGenerationStatus;
import com.razeef.bugbrother.indexes.repository.IndexGenerationRepository;
import com.razeef.bugbrother.indexes.service.RetiredGenerationCleanupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class IndexCleanupScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(IndexCleanupScheduler.class);

    private final IndexGenerationRepository generationRepository;
    private final IndexCleanupCommandPublisher publisher;
    private final RetiredGenerationCleanupService retiredCleanupService;
    private final Duration retiredRetention;

    public IndexCleanupScheduler(
            IndexGenerationRepository generationRepository,
            IndexCleanupCommandPublisher publisher,
            RetiredGenerationCleanupService retiredCleanupService,
            @Value("${bugbrother.index.retired-retention:7d}")
            Duration retiredRetention
    ) {
        this.generationRepository = generationRepository;
        this.publisher = publisher;
        this.retiredCleanupService = retiredCleanupService;
        if (retiredRetention.isNegative() || retiredRetention.isZero()) {
            throw new IllegalArgumentException(
                    "Retired generation retention must be positive"
            );
        }
        this.retiredRetention = retiredRetention;
    }

    @Scheduled(
            fixedDelayString =
                    "${bugbrother.index.cleanup-scan-delay:60000}"
    )
    public void publishPendingCleanups() {
        Instant cutoff = Instant.now().minus(retiredRetention);
        generationRepository
                .findByStatusAndRetiredAtBeforeOrderByRetiredAtAsc(
                        IndexGenerationStatus.RETIRED,
                        cutoff,
                        PageRequest.of(0, 100)
                )
                .forEach(generation -> {
                    try {
                        retiredCleanupService.scheduleIfEligible(
                                generation.getGenerationId(),
                                cutoff
                        );
                    } catch (RuntimeException exception) {
                        LOGGER.warn(
                                "Could not prepare retired generation {} for cleanup",
                                generation.getGenerationId(),
                                exception
                        );
                    }
                });

        generationRepository
                .findByStatusOrderByUpdatedAtAsc(
                        IndexGenerationStatus.CLEANING
                )
                .forEach(generation -> {
                    try {
                        publisher.publish(
                                CleanupIndexGenerationCommandV1.create(
                                        generation.getGenerationId(),
                                        generation.getVectorClientId()
                                )
                        );
                    } catch (RuntimeException exception) {
                        LOGGER.warn(
                                "Could not publish cleanup for generation {}",
                                generation.getGenerationId(),
                                exception
                        );
                    }
                });
    }
}
