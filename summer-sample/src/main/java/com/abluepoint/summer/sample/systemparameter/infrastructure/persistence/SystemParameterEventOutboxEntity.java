package com.abluepoint.summer.sample.systemparameter.infrastructure.persistence;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterRules;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterEventStatus;

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
public class SystemParameterEventOutboxEntity {
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
    @Column(name = "request_id", updatable = false, length = 120)
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

    public SystemParameterEventOutboxEntity(String eventId, String tenantId, String parameterKey,
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

    public String eventId() { return eventId; }
    public String tenantId() { return tenantId; }
    public String parameterKey() { return parameterKey; }
    public String eventType() { return eventType; }
    public long parameterVersion() { return parameterVersion; }
    public String requestId() { return requestId; }
    public String payloadJson() { return payloadJson; }
    public SystemParameterEventStatus status() { return status; }
    public int attempts() { return attempts; }
    public Instant nextAttemptAt() { return nextAttemptAt; }
    public String lastError() { return lastError; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public void markPublished(Instant now) {
        status = SystemParameterEventStatus.PUBLISHED;
        lastError = null;
        updatedAt = now;
    }

    public void markRetry(Instant next, String error) {
        status = SystemParameterEventStatus.PENDING;
        attempts++;
        nextAttemptAt = next;
        lastError = SystemParameterRules.safeError(error);
        updatedAt = Instant.now();
    }

    public void markDeadLetter(String error) {
        status = SystemParameterEventStatus.DEAD_LETTER;
        attempts++;
        lastError = SystemParameterRules.safeError(error);
        updatedAt = Instant.now();
    }

    public void resetForRetry(Instant now) {
        status = SystemParameterEventStatus.PENDING;
        nextAttemptAt = now;
        lastError = null;
        updatedAt = now;
    }

}
