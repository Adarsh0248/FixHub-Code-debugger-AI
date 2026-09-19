package com.razeef.bugbrother.indexing.service;

import com.razeef.bugbrother.events.CleanupIndexGenerationCommandV1;
import com.razeef.bugbrother.indexing.client.ManifestSubmissionClient;
import com.razeef.bugbrother.indexing.model.IndexCleanupPlan;
import com.razeef.bugbrother.vector.service.VectorSearchService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class IndexCleanupWorkerService {

    private final ManifestSubmissionClient manifestClient;
    private final VectorSearchService vectorSearchService;

    public IndexCleanupWorkerService(
            ManifestSubmissionClient manifestClient,
            VectorSearchService vectorSearchService
    ) {
        this.manifestClient = manifestClient;
        this.vectorSearchService = vectorSearchService;
    }

    @KafkaListener(
            topics = "code-guardian-index-cleanup",
            groupId = "code-guardian-index-cleanup-group"
    )
    public void cleanup(CleanupIndexGenerationCommandV1 command) {
        validate(command);

        IndexCleanupPlan plan = manifestClient.fetchCleanupPlan(
                command.generationId()
        );

        // A duplicate command can arrive after the generation was removed.
        if (plan == null) {
            return;
        }

        if (!plan.vectorClientId().equals(command.vectorClientId())) {
            throw new IllegalStateException(
                    "Cleanup command client ID does not match its plan"
            );
        }

        vectorSearchService.deleteVectors(
                plan.vectorClientId(),
                plan.vectorLabels(),
                command.eventId()
        );

        manifestClient.completeCleanup(command.generationId());
    }

    private void validate(CleanupIndexGenerationCommandV1 command) {
        if (command == null
                || command.schemaVersion() != 1
                || command.eventId() == null
                || command.generationId() == null
                || command.vectorClientId() == null
                || command.vectorClientId().isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid index cleanup command"
            );
        }
    }
}
