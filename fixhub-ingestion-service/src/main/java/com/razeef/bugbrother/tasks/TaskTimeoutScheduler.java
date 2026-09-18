package com.razeef.bugbrother.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class TaskTimeoutScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(TaskTimeoutScheduler.class);

    private final TaskRepository taskRepository;
    private final TaskTimeoutService taskTimeoutService;
    private final Duration staleAfter;

    public TaskTimeoutScheduler(
            TaskRepository taskRepository,
            TaskTimeoutService taskTimeoutService,
            @Value("${bugbrother.task.stale-after:30m}")
            Duration staleAfter
    ) {
        if (staleAfter.isZero() || staleAfter.isNegative()) {
            throw new IllegalArgumentException(
                    "bugbrother.task.stale-after must be positive"
            );
        }

        this.taskRepository = taskRepository;
        this.taskTimeoutService = taskTimeoutService;
        this.staleAfter = staleAfter;
    }

    @Scheduled(
            fixedDelayString =
                    "${bugbrother.task.stale-scan-delay:60000}"
    )
    public void checkStaleTasks() {
        Instant cutoff = Instant.now().minus(staleAfter);

        List<TaskEntity> candidates =
                taskRepository.findByStatusNotInAndUpdatedAtBefore(
                        List.of(
                                TaskStatus.COMPLETED,
                                TaskStatus.FAILED
                        ),
                        cutoff
                );

        for (TaskEntity candidate : candidates) {
            if (taskTimeoutService.failIfStale(
                    candidate.getTaskId(),
                    cutoff
            )) {
                log.warn(
                        "Task {} exceeded the worker update timeout",
                        candidate.getTaskId()
                );
            }
        }
    }
}