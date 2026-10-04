package com.abluepoint.summer.sample.key;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "summer_security_key_rotation")
class KeyRotationStateEntity {
    @Id
    @Column(name = "rotation_id")
    private int id;
    @Column(name = "period_days", nullable = false)
    private int periodDays;
    @Column(name = "retention_days", nullable = false)
    private int retentionDays;
    @Column(name = "next_rotation_at", nullable = false)
    private Instant nextRotationAt;
    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;
    @Column(name = "last_success_at")
    private Instant lastSuccessAt;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(name = "failure_count", nullable = false)
    private int failureCount;
    @Column(name = "last_error", length = 240)
    private String lastError;
    @Column(name = "attempted_key_version")
    private Integer attemptedKeyVersion;
    @Version
    private long version;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KeyRotationStateEntity() {}

    KeyRotationStateEntity(int periodDays, int retentionDays, Instant nextRotationAt) {
        this.id = 1;
        this.periodDays = periodDays;
        this.retentionDays = retentionDays;
        this.nextRotationAt = nextRotationAt;
        this.status = "SCHEDULED";
        this.failureCount = 0;
        this.updatedAt = Instant.now();
    }

    int id() { return id; }
    int periodDays() { return periodDays; }
    int retentionDays() { return retentionDays; }
    Instant nextRotationAt() { return nextRotationAt; }
    Instant lastAttemptAt() { return lastAttemptAt; }
    Instant lastSuccessAt() { return lastSuccessAt; }
    String status() { return status; }
    int failureCount() { return failureCount; }
    String lastError() { return lastError; }
    Integer attemptedKeyVersion() { return attemptedKeyVersion; }
    long version() { return version; }
    void schedule(int periodDays, int retentionDays, Instant nextRotationAt) {
        this.periodDays = periodDays; this.retentionDays = retentionDays;
        this.nextRotationAt = nextRotationAt; this.status = "SCHEDULED";
        this.failureCount = 0; this.lastError = null; this.updatedAt = Instant.now();
    }
    void attempt(Instant at, int expectedKeyVersion) {
        this.lastAttemptAt = at; this.attemptedKeyVersion = expectedKeyVersion;
        this.status = "RUNNING"; this.lastError = null; this.updatedAt = at;
    }
    void success(Instant at, Instant nextAt) {
        this.lastSuccessAt = at; this.nextRotationAt = nextAt; this.status = "SUCCEEDED";
        this.attemptedKeyVersion = null;
        this.failureCount = 0; this.lastError = null; this.updatedAt = at;
    }
    void failure(Instant at, String error, Instant retryAt) {
        this.status = "FAILED"; this.failureCount++; this.lastError = error;
        this.nextRotationAt = retryAt; this.updatedAt = at;
    }
}
