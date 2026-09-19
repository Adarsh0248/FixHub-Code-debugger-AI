package com.razeef.bugbrother.indexes.scheduler;

import com.razeef.bugbrother.events.CleanupIndexGenerationCommandV1;
import com.razeef.bugbrother.indexes.messaging.IndexCleanupCommandPublisher;
import com.razeef.bugbrother.indexes.model.IndexGenerationStatus;
import com.razeef.bugbrother.indexes.repository.IndexGenerationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IndexCleanupScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(IndexCleanupScheduler.class);

    private final IndexGenerationRepository generationRepository;
    private final IndexCleanupCommandPublisher publisher;

    public IndexCleanupScheduler(
            IndexGenerationRepository generationRepository,
            IndexCleanupCommandPublisher publisher
    ) {
        this.generationRepository = generationRepository;
        this.publisher = publisher;
    }

    @Scheduled(
            fixedDelayString =
                    "${bugbrother.index.cleanup-scan-delay:60000}"
    )
    public void publishPendingCleanups() {
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
