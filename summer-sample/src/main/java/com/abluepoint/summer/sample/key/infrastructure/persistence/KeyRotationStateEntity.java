package com.abluepoint.summer.sample.key.infrastructure.persistence;

import com.abluepoint.summer.sample.key.domain.KeyRotationState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "summer_security_key_rotation")
public class KeyRotationStateEntity {
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

    public KeyRotationStateEntity(int periodDays, int retentionDays, Instant nextRotationAt) {
        this.id = 1;
        apply(new KeyRotationState(periodDays, retentionDays, nextRotationAt));
    }

    public int id() { return id; }
    public int periodDays() { return periodDays; }
    public int retentionDays() { return retentionDays; }
    public Instant nextRotationAt() { return nextRotationAt; }
    public Instant lastAttemptAt() { return lastAttemptAt; }
    public Instant lastSuccessAt() { return lastSuccessAt; }
    public String status() { return status; }
    public int failureCount() { return failureCount; }
    public String lastError() { return lastError; }
    public Integer attemptedKeyVersion() { return attemptedKeyVersion; }
    public long version() { return version; }
    KeyRotationState toDomain() {
        return new KeyRotationState(periodDays, retentionDays, nextRotationAt, lastAttemptAt, lastSuccessAt,
                status, failureCount, lastError, attemptedKeyVersion, updatedAt);
    }
    void apply(KeyRotationState state) {
        periodDays = state.periodDays(); retentionDays = state.retentionDays(); nextRotationAt = state.nextRotationAt();
        lastAttemptAt = state.lastAttemptAt(); lastSuccessAt = state.lastSuccessAt(); status = state.status();
        failureCount = state.failureCount(); lastError = state.lastError(); attemptedKeyVersion = state.attemptedKeyVersion();
        updatedAt = state.updatedAt();
    }
    public void schedule(int period, int retention, Instant next) { var state = toDomain(); state.schedule(period, retention, next); apply(state); }
    public void attempt(Instant at, int expected) { var state = toDomain(); state.attempt(at, expected); apply(state); }
    public void success(Instant at, Instant next) { var state = toDomain(); state.success(at, next); apply(state); }
    public void failure(Instant at, String error, Instant retry) { var state = toDomain(); state.failure(at, error, retry); apply(state); }
}
