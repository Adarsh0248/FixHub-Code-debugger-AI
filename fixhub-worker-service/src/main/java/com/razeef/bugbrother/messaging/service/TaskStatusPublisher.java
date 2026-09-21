package com.razeef.bugbrother.messaging.service;

import com.razeef.bugbrother.events.TaskStatusEventV2;
import com.razeef.bugbrother.messaging.exception.TaskStatusPublicationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service 
public class TaskStatusPublisher {
    
    private static final String TOPIC =
            "code-guardian-task-events-v2";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Duration publishTimeout;
    private final WebClient ingestionClient;
    private final String workerKey;

    public TaskStatusPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${bugbrother.kafka.publish-timeout:10s}")
            Duration publishTimeout,
            WebClient.Builder webClientBuilder,
            @Value("${bugbrother.ingestion.base-url}") String ingestionBaseUrl,
            @Value("${bugbrother.internal.worker-key}") String workerKey
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.publishTimeout = publishTimeout;
        this.ingestionClient = webClientBuilder
                .baseUrl(ingestionBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
        this.workerKey = workerKey;
    }

    public void running(
            UUID taskId,
            long sequence,
            String stage,
            String message
    ) {
        publish(
                taskId,
                sequence,
                "RUNNING",
                stage,
                null,
                null,
                message,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public void completed(
            UUID taskId,
            long sequence,
            Integer progressCurrent,
            Integer progressTotal,
            String message,
            String validationSummary
    ) {
        publish(
                taskId,
                sequence,
                "COMPLETED",
                "COMPLETED",
                progressCurrent,
                progressTotal,
                message,
                null,
                null,
                null,
                null,
                null,
                null,
                validationSummary
        );
    }

    public void completedWithResult(
            UUID taskId,
            long sequence,
            Integer progressCurrent,
            Integer progressTotal,
            String message,
            String resultBranch,
            String resultCommitSha,
            String resultUrl,
            String resultExplanation,
            String validationSummary
    ) {
        publish(
                taskId,
                sequence,
                "COMPLETED",
                "COMPLETED",
                progressCurrent,
                progressTotal,
                message,
                null,
                null,
                resultBranch,
                resultCommitSha,
                resultUrl,
                resultExplanation,
                validationSummary
        );
    }

    public void failed(
            UUID taskId,
            long sequence,
            String errorCode,
            Throwable exception
    ) {
        publish(
                taskId,
                sequence,
                "FAILED",
                "FAILED",
                null,
                null,
                "Task failed",
                errorCode,
                safeMessage(exception),
                null,
                null,
                null,
                null,
                null
        );
    }

     private void publish(
            UUID taskId,
            long sequence,
            String status,
            String stage,
            Integer progressCurrent,
            Integer progressTotal,
            String message,
            String errorCode,
            String errorMessage,
            String resultBranch,
            String resultCommitSha,
            String resultUrl,
            String resultExplanation,
            String validationSummary
    ) {
        TaskStatusEventV2 event = new TaskStatusEventV2(
                UUID.randomUUID(),
                taskId,
                sequence,
                status,
                stage,
                progressCurrent,
                progressTotal,
                message,
                errorCode,
                errorMessage,
                resultBranch,
                resultCommitSha,
                resultUrl,
                resultExplanation,
                validationSummary,
                Instant.now()
        );
        
        try{
            kafkaTemplate.send(TOPIC, taskId.toString(), event)
            .get(
                publishTimeout.toMillis(),
                TimeUnit.MILLISECONDS
            );
        }catch(Exception exception){
            try {
                ingestionClient.post()
                        .uri("/internal/tasks/status-events")
                        .header("X-BugBrother-Worker-Key", workerKey)
                        .bodyValue(event)
                        .retrieve()
                        .toBodilessEntity()
                        .timeout(publishTimeout)
                        .block();
            } catch (RuntimeException fallbackFailure) {
                fallbackFailure.addSuppressed(exception);
                throw new TaskStatusPublicationException(fallbackFailure);
            }
        }
    }

    private String safeMessage(Throwable exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }

        return message.length() <= 1000
                ? message
                : message.substring(0, 1000);
    }
}
