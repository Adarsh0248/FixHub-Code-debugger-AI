package com.razeef.bugbrother.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.razeef.bugbrother.events.TaskStatusEventV1;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskEntity {

    @Id
    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 32)
    private TaskType taskType;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "repository_id")
    private Long repositoryId;

    @Column(name = "owner", nullable = false, length = 255)
    private String owner;

    @Column(name = "repo", nullable = false, length = 255)
    private String repo;

    @Column(name = "branch", length = 255)
    private String branch;

    @Column(name = "base_commit_sha", length = 64)
    private String baseCommitSha;

    @Column(name = "request_summary", columnDefinition = "text")
    private String requestSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 64)
    private TaskStage stage;

    @Column(name = "event_sequence", nullable = false)
    private long eventSequence;

    @Column(name = "progress_current")
    private Integer progressCurrent;

    @Column(name = "progress_total")
    private Integer progressTotal;

    @Column(name = "status_message", length = 1000)
    private String statusMessage;

    @Column(name = "error_code", length = 128)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "result_branch", length = 512)
    private String resultBranch;

    @Column(name = "result_commit_sha", length = 64)
    private String resultCommitSha;

    @Column(name = "result_url", length = 1000)
    private String resultUrl;

    @Column(name = "validation_summary", length = 2000)
    private String validationSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static TaskEntity queued(
            TaskType taskType,
            String userId,
            String owner,
            String repo,
            String requestSummary
    ) {
        TaskEntity task = new TaskEntity();

        Instant now = Instant.now();

        task.taskId = UUID.randomUUID();
        task.taskType = taskType;
        task.userId = userId;
        task.owner = owner;
        task.repo = repo;
        task.requestSummary = requestSummary;

        task.status = TaskStatus.QUEUED;
        task.stage = TaskStage.QUEUED;
        task.eventSequence = 0;
        task.statusMessage = "Waiting for a worker";

        task.createdAt = now;
        task.updatedAt = now;

        return task;
    }

    public void markPublicationFailed(String errorMessage) {
    if (status.isTerminal()) {
        return;
    }

    this.status = TaskStatus.FAILED;
    this.stage = TaskStage.PUBLICATION_FAILED;
    this.statusMessage = "Could not publish the command to Kafka";
    this.errorCode = "COMMAND_PUBLICATION_FAILED";
    this.errorMessage = errorMessage;
    this.updatedAt = Instant.now();
}

    public void markWorkerTimeout() {
        if (status.isTerminal()) {
            return;
        }

        this.status = TaskStatus.FAILED;
        this.stage = TaskStage.WORKER_TIMEOUT;
        this.statusMessage = "The worker stopped updating this task";
        this.errorCode = "WORKER_TIMEOUT";
        this.errorMessage = "No worker update was received before the configured timeout";
        this.updatedAt = Instant.now();
    }

    public boolean applyStatusEvent(TaskStatusEventV1 event) {
        if (this.status.isTerminal()) {
            return false;
        }

        if (event.sequence() <= this.eventSequence) {
            return false;
        }

        this.status = TaskStatus.valueOf(event.status());
        this.stage = TaskStage.valueOf(event.stage());

        this.eventSequence = event.sequence();
        this.progressCurrent = event.progressCurrent();
        this.progressTotal = event.progressTotal();
        this.statusMessage = event.message();

        this.errorCode = event.errorCode();
        this.errorMessage = event.errorMessage();

        this.resultBranch = event.resultBranch();
        this.resultCommitSha = event.resultCommitSha();
        this.resultUrl = event.resultUrl();

        this.validationSummary = event.validationSummary();

        this.updatedAt = event.occurredAt() == null
                ? Instant.now()
                : event.occurredAt();

        return true;
    }
}