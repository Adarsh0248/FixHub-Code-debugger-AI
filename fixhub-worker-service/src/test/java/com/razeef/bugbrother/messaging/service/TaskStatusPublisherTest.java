package com.razeef.bugbrother.messaging.service;

import com.razeef.bugbrother.messaging.exception.TaskStatusPublicationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskStatusPublisherTest {

    @Test
    void appliesEventThroughInternalApiWhenKafkaIsUnavailable() {
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(), any(), any())).thenReturn(
                CompletableFuture.failedFuture(
                        new IllegalStateException("Kafka unavailable")));
        AtomicBoolean fallbackCalled = new AtomicBoolean();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(
                request -> {
                    assertTrue(request.url().getPath().endsWith(
                            "/internal/tasks/status-events"));
                    fallbackCalled.set(true);
                    return Mono.just(ClientResponse.create(
                            HttpStatus.NO_CONTENT).build());
                });
        TaskStatusPublisher publisher = new TaskStatusPublisher(kafka,
                Duration.ofSeconds(1), builder, "http://localhost:8080",
                "worker-key");

        publisher.running(UUID.randomUUID(), 1, "STARTING", "Started");

        assertTrue(fallbackCalled.get());
    }

    @Test
    void keepsCommandRetryableWhenBothStatusPathsFail() {
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(), any(), any())).thenReturn(
                CompletableFuture.failedFuture(
                        new IllegalStateException("Kafka unavailable")));
        WebClient.Builder builder = WebClient.builder().exchangeFunction(
                request -> Mono.error(new IllegalStateException(
                        "Ingestion unavailable")));
        TaskStatusPublisher publisher = new TaskStatusPublisher(kafka,
                Duration.ofSeconds(1), builder, "http://localhost:8080",
                "worker-key");

        assertThrows(TaskStatusPublicationException.class,
                () -> publisher.running(UUID.randomUUID(), 1,
                        "STARTING", "Started"));
    }
}
