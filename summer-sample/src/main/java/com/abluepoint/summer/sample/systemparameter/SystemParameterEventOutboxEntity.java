package com.abluepoint.summer.sample.systemparameter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "summer_system_parameter_outbox")
class SystemParameterEventOutboxEntity {
    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 80)
    private String tenantId;
    @Column(name = "parameter_key", nullable = false, updatable = false, length = 120)
    private String parameterKey;
    @Column(name = "event_type", nullable = false, updatable = false, length = 80)
    private String eventType;
    @Column(name = "parameter_version", nullable = false, updatable = false)
    private long parameterVersion;
    @Column(name = "request_id", updatable = false, length = 128)
    private String requestId;
    @Lob
    @Column(name = "payload_json", nullable = false, updatable = false)
    private String payloadJson;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SystemParameterEventStatus status = SystemParameterEventStatus.PENDING;
    @Column(name = "attempts", nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "last_error", length = 500)
    private String lastError;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SystemParameterEventOutboxEntity() {}

    SystemParameterEventOutboxEntity(String eventId, String tenantId, String parameterKey,
                                     String eventType, long parameterVersion, String requestId,
                                     String payloadJson, Instant now) {
        this.eventId = eventId;
        this.tenantId = tenantId;
        this.parameterKey = parameterKey;
        this.eventType = eventType;
        this.parameterVersion = parameterVersion;
        this.requestId = requestId;
        this.payloadJson = payloadJson;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    String eventId() { return eventId; }
    String tenantId() { return tenantId; }
    String parameterKey() { return parameterKey; }
    String eventType() { return eventType; }
    long parameterVersion() { return parameterVersion; }
    String requestId() { return requestId; }
    String payloadJson() { return payloadJson; }
    SystemParameterEventStatus status() { return status; }
    int attempts() { return attempts; }
    Instant nextAttemptAt() { return nextAttemptAt; }
    String lastError() { return lastError; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }

    void markPublished(Instant now) {
        status = SystemParameterEventStatus.PUBLISHED;
        lastError = null;
        updatedAt = now;
    }

    void markRetry(Instant next, String error) {
        status = SystemParameterEventStatus.PENDING;
        attempts++;
        nextAttemptAt = next;
        lastError = safeError(error);
        updatedAt = Instant.now();
    }

    void markDeadLetter(String error) {
        status = SystemParameterEventStatus.DEAD_LETTER;
        attempts++;
        lastError = safeError(error);
        updatedAt = Instant.now();
    }

    void resetForRetry(Instant now) {
        status = SystemParameterEventStatus.PENDING;
        nextAttemptAt = now;
        lastError = null;
        updatedAt = now;
    }

    private static String safeError(String error) {
        if (error == null || error.isBlank()) return "delivery_failed";
        return error.length() <= 500 ? error : error.substring(0, 500);
    }
}

enum SystemParameterEventStatus { PENDING, PUBLISHED, DEAD_LETTER }
