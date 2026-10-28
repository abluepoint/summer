package com.abluepoint.summer.sample.key.domain;

import java.time.Instant;

public class KeyRotationState {

    private int periodDays;
    private int retentionDays;
    private Instant nextRotationAt;

    private Instant lastAttemptAt;

    private Instant lastSuccessAt;

    private String status;

    private int failureCount;

    private String lastError;

    private Integer attemptedKeyVersion;

    private Instant updatedAt;

    public KeyRotationState(int periodDays, int retentionDays, Instant nextRotationAt) {
        this.periodDays = periodDays;
        this.retentionDays = retentionDays;
        this.nextRotationAt = nextRotationAt;
        this.status = "SCHEDULED";
        this.failureCount = 0;
        this.updatedAt = Instant.now();
    }

    public int periodDays() { return periodDays; }
    public int retentionDays() { return retentionDays; }
    public Instant nextRotationAt() { return nextRotationAt; }
    public Instant lastAttemptAt() { return lastAttemptAt; }
    public Instant lastSuccessAt() { return lastSuccessAt; }
    public String status() { return status; }
    public int failureCount() { return failureCount; }
    public String lastError() { return lastError; }
    public Integer attemptedKeyVersion() { return attemptedKeyVersion; }
    public void schedule(int periodDays, int retentionDays, Instant nextRotationAt) {
        this.periodDays = periodDays; this.retentionDays = retentionDays;
        this.nextRotationAt = nextRotationAt; this.status = "SCHEDULED";
        this.failureCount = 0; this.lastError = null; this.updatedAt = Instant.now();
    }
    public void attempt(Instant at, int expectedKeyVersion) {
        this.lastAttemptAt = at; this.attemptedKeyVersion = expectedKeyVersion;
        this.status = "RUNNING"; this.lastError = null; this.updatedAt = at;
    }
    public void success(Instant at, Instant nextAt) {
        this.lastSuccessAt = at; this.nextRotationAt = nextAt; this.status = "SUCCEEDED";
        this.attemptedKeyVersion = null;
        this.failureCount = 0; this.lastError = null; this.updatedAt = at;
    }
    public void failure(Instant at, String error, Instant retryAt) {
        this.status = "FAILED"; this.failureCount++; this.lastError = error;
        this.nextRotationAt = retryAt; this.updatedAt = at;
    }

    public KeyRotationState(int periodDays, int retentionDays, Instant nextRotationAt, Instant lastAttemptAt,
                            Instant lastSuccessAt, String status, int failureCount, String lastError,
                            Integer attemptedKeyVersion, Instant updatedAt) {
        this.periodDays = periodDays; this.retentionDays = retentionDays; this.nextRotationAt = nextRotationAt;
        this.lastAttemptAt = lastAttemptAt; this.lastSuccessAt = lastSuccessAt; this.status = status;
        this.failureCount = failureCount; this.lastError = lastError; this.attemptedKeyVersion = attemptedKeyVersion;
        this.updatedAt = updatedAt;
    }
    public Instant updatedAt() { return updatedAt; }
    public boolean dueAt(Instant now) { return !nextRotationAt.isAfter(now); }
    public boolean runningAt(Instant now) {
        return "RUNNING".equals(status) && lastAttemptAt != null && lastAttemptAt.plus(java.time.Duration.ofMinutes(10)).isAfter(now);
    }
    public boolean alreadyRotated(int currentVersion) { return attemptedKeyVersion != null && currentVersion > attemptedKeyVersion; }
    public Instant retryAt(Instant now) {
        int failures = failureCount + 1;
        long hours = Math.min(24L, 1L << Math.min(5, Math.max(0, failures - 1)));
        return now.plus(java.time.Duration.ofHours(hours));
    }
}
