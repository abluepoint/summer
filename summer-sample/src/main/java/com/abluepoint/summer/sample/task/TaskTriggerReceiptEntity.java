package com.abluepoint.summer.sample.task;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "summer_task_trigger_receipt", uniqueConstraints = @UniqueConstraint(
        name = "uk_summer_task_trigger_key", columnNames = {"enterprise_id", "idempotency_key"}))
class TaskTriggerReceiptEntity {
    @Id @Column(name = "receipt_id", nullable = false, updatable = false, length = 36) private String receiptId;
    @Column(name = "enterprise_id", nullable = false, updatable = false, length = 64) private String enterpriseId;
    @Column(name = "task_id", nullable = false, updatable = false, length = 36) private String taskId;
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 255) private String idempotencyKey;
    @Column(nullable = false, length = 128) private String fingerprint;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected TaskTriggerReceiptEntity() {}
    TaskTriggerReceiptEntity(String receiptId, String enterpriseId, String taskId, String idempotencyKey, String fingerprint) {
        this.receiptId = receiptId; this.enterpriseId = enterpriseId; this.taskId = taskId;
        this.idempotencyKey = idempotencyKey; this.fingerprint = fingerprint; this.createdAt = Instant.now();
    }
    String taskId() { return taskId; } String fingerprint() { return fingerprint; }
}
