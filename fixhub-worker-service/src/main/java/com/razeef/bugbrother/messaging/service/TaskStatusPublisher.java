package com.razeef.bugbrother.messaging.service;

import com.razeef.bugbrother.events.TaskStatusEventV1;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service 
public class TaskStatusPublisher {
    
    private static final String TOPIC =
            "code-guardian-task-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Duration publishTimeout;

    public TaskStatusPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${bugbrother.kafka.publish-timeout:10s}")
            Duration publishTimeout
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.publishTimeout = publishTimeout;
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
            String validationSummary
    ) {
        TaskStatusEventV1 event = new TaskStatusEventV1(
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
            throw new IllegalStateException(
                "Could not publish task status",
                exception
            );
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
