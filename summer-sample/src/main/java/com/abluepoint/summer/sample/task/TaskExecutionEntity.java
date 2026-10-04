package com.abluepoint.summer.sample.task;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "summer_task_execution", uniqueConstraints = {
        @UniqueConstraint(name = "uk_summer_task_execution_fire", columnNames = {"task_id", "fire_instance_id"}),
        @UniqueConstraint(name = "uk_summer_task_execution_key", columnNames = {"task_id", "idempotency_key"})
})
class TaskExecutionEntity {
    @Id @Column(name = "execution_id", nullable = false, updatable = false, length = 36) private String executionId;
    @Column(name = "task_id", nullable = false, updatable = false, length = 36) private String taskId;
    @Column(name = "enterprise_id", nullable = false, updatable = false, length = 64) private String enterpriseId;
    @Column(name = "fire_instance_id", nullable = false, updatable = false, length = 200) private String fireInstanceId;
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 255) private String idempotencyKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private TaskExecutionStatus status;
    @Column(nullable = false) private int attempt;
    @Column(name = "started_at", nullable = false, updatable = false) private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "failure_code", length = 120) private String failureCode;
    @Column(name = "node_id", length = 120) private String nodeId;
    @Column(name = "lease_until") private Instant leaseUntil;
    @Column(name = "recovery_count", nullable = false) private int recoveryCount;

    protected TaskExecutionEntity() {}
    TaskExecutionEntity(String executionId, String taskId, String enterpriseId, String fireInstanceId, String idempotencyKey,
                        String nodeId, Instant leaseUntil) {
        this.executionId = executionId; this.taskId = taskId; this.enterpriseId = enterpriseId;
        this.fireInstanceId = fireInstanceId; this.idempotencyKey = idempotencyKey;
        this.status = TaskExecutionStatus.RUNNING; this.attempt = 0; this.recoveryCount = 0;
        this.nodeId = nodeId; this.leaseUntil = leaseUntil; this.startedAt = Instant.now();
    }
    TaskExecutionEntity(String executionId, String taskId, String enterpriseId, String fireInstanceId) {
        this(executionId, taskId, enterpriseId, fireInstanceId, fireInstanceId, "unknown", Instant.now().plusSeconds(30));
    }
    void attempt(int value) { attempt = value; }
    void claim(String nodeId, Instant leaseUntil, boolean recovered) { this.nodeId = nodeId; this.leaseUntil = leaseUntil; if (recovered) recoveryCount++; }
    void success() { status = TaskExecutionStatus.SUCCESS; finishedAt = Instant.now(); leaseUntil = null; }
    void failure(String code) { status = TaskExecutionStatus.FAILED; failureCode = code; finishedAt = Instant.now(); leaseUntil = null; }
    String executionId() { return executionId; } String taskId() { return taskId; } String enterpriseId() { return enterpriseId; }
    String fireInstanceId() { return fireInstanceId; } String idempotencyKey() { return idempotencyKey; }
    TaskExecutionStatus status() { return status; } int attempt() { return attempt; }
    Instant startedAt() { return startedAt; } Instant finishedAt() { return finishedAt; } String failureCode() { return failureCode; }
    String nodeId() { return nodeId; } Instant leaseUntil() { return leaseUntil; } int recoveryCount() { return recoveryCount; }
}
enum TaskExecutionStatus { RUNNING, SUCCESS, FAILED }
