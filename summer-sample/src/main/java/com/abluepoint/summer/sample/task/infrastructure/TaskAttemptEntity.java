package com.abluepoint.summer.sample.task.infrastructure;
import com.abluepoint.summer.sample.task.domain.TaskExecutionStatus;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "summer_task_execution_attempt", uniqueConstraints = @UniqueConstraint(
        name = "uk_summer_task_attempt_number", columnNames = {"execution_id", "attempt"}))
public class TaskAttemptEntity {
    @Id @Column(name = "attempt_id", nullable = false, updatable = false, length = 36) private String attemptId;
    @Column(name = "execution_id", nullable = false, updatable = false, length = 36) private String executionId;
    @Column(nullable = false) private int attempt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private TaskExecutionStatus status;
    @Column(name = "started_at", nullable = false, updatable = false) private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "failure_code", length = 120) private String failureCode;
    @Column(name = "node_id", length = 120) private String nodeId;
    protected TaskAttemptEntity() {}
    public TaskAttemptEntity(String attemptId, String executionId, int attempt, String nodeId) {
        this.attemptId = attemptId; this.executionId = executionId; this.attempt = attempt;
        this.status = TaskExecutionStatus.RUNNING; this.nodeId = nodeId; this.startedAt = Instant.now();
    }
    public void success() { status = TaskExecutionStatus.SUCCESS; finishedAt = Instant.now(); }
    public void failure(String code, TaskExecutionStatus finalStatus) { status = finalStatus; failureCode = code; finishedAt = Instant.now(); }
    public int attempt() { return attempt; } public TaskExecutionStatus status() { return status; }
    public Instant startedAt() { return startedAt; } public Instant finishedAt() { return finishedAt; }
    public String failureCode() { return failureCode; } public String nodeId() { return nodeId; }
}
