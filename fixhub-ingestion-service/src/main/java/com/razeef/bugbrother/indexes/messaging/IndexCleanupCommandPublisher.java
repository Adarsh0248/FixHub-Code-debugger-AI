package com.razeef.bugbrother.indexes.messaging;

import com.razeef.bugbrother.events.CleanupIndexGenerationCommandV1;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class IndexCleanupCommandPublisher {

    private static final String TOPIC =
            "code-guardian-index-cleanup";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Duration publishTimeout;

    public IndexCleanupCommandPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${bugbrother.kafka.publish-timeout:10s}")
            Duration publishTimeout
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.publishTimeout = publishTimeout;
    }

    public void publish(CleanupIndexGenerationCommandV1 command) {
        try {
            kafkaTemplate.send(
                    TOPIC,
                    command.generationId().toString(),
                    command
            ).get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not publish index cleanup command",
                    exception
            );
        }
    }
}
