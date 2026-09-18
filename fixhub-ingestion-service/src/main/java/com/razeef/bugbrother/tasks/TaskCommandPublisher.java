package com.razeef.bugbrother.tasks;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service 
public class TaskCommandPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TaskService taskService;
    private final Duration publishTimeout;

    public TaskCommandPublisher(
        KafkaTemplate<String, Object> kafkaTemplate,
        TaskService taskService,
        @Value("${bugbrother.kafka.publish-timeout:10s}")
        Duration publishTimeout
    ){
        this.kafkaTemplate = kafkaTemplate;
        this.taskService = taskService;
        this.publishTimeout = publishTimeout;
    }

    public void publish(
        String topic,
        UUID taskId,
        Object command
    ){
        try{
            kafkaTemplate.send(topic, taskId.toString(), command)
            .get(
                publishTimeout.toMillis(),
                TimeUnit.MILLISECONDS
            );
        } catch(Exception exception){
            taskService.markPublicationFailed(
                taskId,
                exception.getMessage()
            );

            throw new TaskPublicationException(exception);
        }
    }
}
