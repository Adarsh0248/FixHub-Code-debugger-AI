package com.razeef.bugbrother.tasks.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razeef.bugbrother.events.DebugRepositoryCommandV3;
import com.razeef.bugbrother.events.IndexRepositoryCommandV2;
import com.razeef.bugbrother.tasks.model.TaskCommandOutboxEntity;
import com.razeef.bugbrother.tasks.repository.TaskCommandOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TaskCommandPublisher {

    private final TaskCommandOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public TaskCommandPublisher(
            TaskCommandOutboxRepository outboxRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    // The caller's transaction must save the task and this command together.
    @Transactional(propagation = Propagation.MANDATORY)
    public void stage(String topic, UUID taskId, Object command) {
        String type;
        if (command instanceof IndexRepositoryCommandV2) {
            type = "INDEX_V2";
        } else if (command instanceof DebugRepositoryCommandV3) {
            type = "DEBUG_V3";
        } else {
            throw new IllegalArgumentException("Unsupported task command type");
        }

        try {
            outboxRepository.save(TaskCommandOutboxEntity.pending(
                    taskId, topic, type,
                    objectMapper.writeValueAsString(command)
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not stage task command", exception);
        }
    }
}
