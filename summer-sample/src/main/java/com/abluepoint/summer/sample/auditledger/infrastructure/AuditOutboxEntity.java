package com.abluepoint.summer.sample.auditledger.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "aict_audit_outbox")
public class AuditOutboxEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, unique = true, updatable = false, length = 160)
    private String eventId;
    @Column(name = "enterprise_id", nullable = false, updatable = false, length = 80)
    private String enterpriseId;
    @Lob
    @Column(name = "payload_json", nullable = false, updatable = false)
    private String payloadJson;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AuditOutboxStatus status = AuditOutboxStatus.PENDING;
    @Column(name = "attempts", nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditOutboxEntity() {
    }

    public AuditOutboxEntity(String eventId, String enterpriseId, String payloadJson, Instant nextAttemptAt) {
        if (enterpriseId == null || enterpriseId.isBlank()) {
            throw new IllegalArgumentException("enterpriseId is required for audit outbox");
        }
        this.eventId = eventId;
        this.enterpriseId = enterpriseId;
        this.payloadJson = payloadJson;
        this.nextAttemptAt = nextAttemptAt;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getEnterpriseId() { return enterpriseId; }
    public String getPayloadJson() { return payloadJson; }
    public AuditOutboxStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }

    void markPublished() { this.status = AuditOutboxStatus.PUBLISHED; }
    void reschedule(Instant next) { this.nextAttemptAt = next; this.attempts++; }
    void deadLetter() { this.status = AuditOutboxStatus.DEAD_LETTER; this.attempts++; }
}

