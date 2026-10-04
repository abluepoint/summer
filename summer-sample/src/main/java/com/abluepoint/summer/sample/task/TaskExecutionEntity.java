package com.abluepoint.summer.sample.task;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "summer_task_execution", uniqueConstraints = @UniqueConstraint(name = "uk_summer_task_execution_fire", columnNames = {"task_id", "fire_instance_id"}))
class TaskExecutionEntity {
    @Id @Column(name = "execution_id", nullable = false, updatable = false, length = 36) private String executionId;
    @Column(name = "task_id", nullable = false, updatable = false, length = 36) private String taskId;
    @Column(name = "enterprise_id", nullable = false, updatable = false, length = 64) private String enterpriseId;
    @Column(name = "fire_instance_id", nullable = false, updatable = false, length = 200) private String fireInstanceId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private TaskExecutionStatus status;
    @Column(nullable = false) private int attempt;
    @Column(name = "started_at", nullable = false, updatable = false) private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "failure_code", length = 120) private String failureCode;

    protected TaskExecutionEntity() {}
    TaskExecutionEntity(String executionId, String taskId, String enterpriseId, String fireInstanceId) {
        this.executionId = executionId; this.taskId = taskId; this.enterpriseId = enterpriseId;
        this.fireInstanceId = fireInstanceId; this.status = TaskExecutionStatus.RUNNING; this.attempt = 1; this.startedAt = Instant.now();
    }
    void attempt(int value) { attempt = value; }
    void success() { status = TaskExecutionStatus.SUCCESS; finishedAt = Instant.now(); }
    void failure(String code) { status = TaskExecutionStatus.FAILED; failureCode = code; finishedAt = Instant.now(); }
    String executionId() { return executionId; } String taskId() { return taskId; } String enterpriseId() { return enterpriseId; }
    String fireInstanceId() { return fireInstanceId; } TaskExecutionStatus status() { return status; } int attempt() { return attempt; }
    Instant startedAt() { return startedAt; } Instant finishedAt() { return finishedAt; } String failureCode() { return failureCode; }
}
enum TaskExecutionStatus { RUNNING, SUCCESS, FAILED }
