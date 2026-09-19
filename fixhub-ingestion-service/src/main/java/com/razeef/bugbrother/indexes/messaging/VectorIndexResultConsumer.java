package com.razeef.bugbrother.indexes.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razeef.bugbrother.indexes.dto.event.VectorIndexResultEvent;
import com.razeef.bugbrother.indexes.service.IndexAcknowledgementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class VectorIndexResultConsumer {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    VectorIndexResultConsumer.class
            );

    private final ObjectMapper objectMapper;
    private final IndexAcknowledgementService acknowledgementService;

    public VectorIndexResultConsumer(
            ObjectMapper objectMapper,
            IndexAcknowledgementService acknowledgementService
    ) {
        this.objectMapper = objectMapper;
        this.acknowledgementService = acknowledgementService;
    }

    @KafkaListener(
            topics = "${bugbrother.vector-results.topic}",
            groupId = "${bugbrother.vector-results.group-id}",
            containerFactory =
                    "vectorResultKafkaListenerContainerFactory"
    )
    public void consume(String json) {
        VectorIndexResultEvent event;

        try {
            event = objectMapper.readValue(
                    json,
                    VectorIndexResultEvent.class
            );
        } catch (JsonProcessingException exception) {
            LOGGER.error(
                    "Discarding malformed vector result event",
                    exception
            );
            return;
        }

        acknowledgementService.apply(event);
    }
}
