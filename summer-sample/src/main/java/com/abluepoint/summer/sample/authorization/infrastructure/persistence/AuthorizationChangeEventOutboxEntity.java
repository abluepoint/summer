package com.abluepoint.summer.sample.authorization.infrastructure.persistence;

import com.abluepoint.summer.sample.authorization.domain.AuthorizationChangeEventStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "summer_authorization_change_outbox")
public class AuthorizationChangeEventOutboxEntity {
    @Id @Column(name = "event_id", nullable = false, updatable = false, length = 36) private String eventId;
    @Column(name = "enterprise_id", nullable = false, updatable = false, length = 64) private String enterpriseId;
    @Column(name = "resource_type", nullable = false, updatable = false, length = 40) private String resourceType;
    @Column(name = "resource_id", nullable = false, updatable = false, length = 200) private String resourceId;
    @Column(name = "event_type", nullable = false, updatable = false, length = 80) private String eventType;
    @Column(name = "resource_version", nullable = false, updatable = false) private long resourceVersion;
    @Column(name = "request_id", updatable = false, length = 120) private String requestId;
    @Lob @Column(name = "payload_json", nullable = false, updatable = false) private String payloadJson;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20) private AuthorizationChangeEventStatus status = AuthorizationChangeEventStatus.PENDING;
    @Column(name = "attempts", nullable = false) private int attempts;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected AuthorizationChangeEventOutboxEntity() {}

    public AuthorizationChangeEventOutboxEntity(String eventId, String enterpriseId, String resourceType, String resourceId,
                                         String eventType, long resourceVersion, String requestId, String payloadJson,
                                         Instant now) {
        this.eventId = eventId; this.enterpriseId = enterpriseId; this.resourceType = resourceType;
        this.resourceId = resourceId; this.eventType = eventType; this.resourceVersion = resourceVersion;
        this.requestId = requestId; this.payloadJson = payloadJson; this.nextAttemptAt = now;
        this.createdAt = now; this.updatedAt = now;
    }

    public String eventId() { return eventId; }
    public String enterpriseId() { return enterpriseId; }
    public String resourceType() { return resourceType; }
    public String resourceId() { return resourceId; }
    public String eventType() { return eventType; }
    public long resourceVersion() { return resourceVersion; }
    public String requestId() { return requestId; }
    public String payloadJson() { return payloadJson; }
    public AuthorizationChangeEventStatus status() { return status; }
    public int attempts() { return attempts; }
    public Instant nextAttemptAt() { return nextAttemptAt; }
    public String lastError() { return lastError; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public void markPublished(Instant now) { status = AuthorizationChangeEventStatus.PUBLISHED; lastError = null; updatedAt = now; }
    public void markRetry(Instant next, String error) { status = AuthorizationChangeEventStatus.PENDING; attempts++; nextAttemptAt = next; lastError = safe(error); updatedAt = Instant.now(); }
    public void markDeadLetter(String error) { status = AuthorizationChangeEventStatus.DEAD_LETTER; attempts++; lastError = safe(error); updatedAt = Instant.now(); }
    public void resetForRetry(Instant now) { status = AuthorizationChangeEventStatus.PENDING; nextAttemptAt = now; lastError = null; updatedAt = now; }
    private static String safe(String error) { if (error == null || error.isBlank()) return "delivery_failed"; return error.length() <= 500 ? error : error.substring(0, 500); }
}
