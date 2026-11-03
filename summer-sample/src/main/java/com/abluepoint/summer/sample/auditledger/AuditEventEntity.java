package com.abluepoint.summer.sample.auditledger;

import com.abluepoint.summer.common.error.SummerException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

/** PostgreSQL/JPA write model for the immutable audit ledger. */
@Entity
@Table(name = "aict_audit_event")
public class AuditEventEntity {
    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 160)
    private String eventId;
    @Column(name = "event_type", nullable = false, updatable = false, length = 80)
    private String eventType;
    @Column(name = "enterprise_id", nullable = false, length = 80, updatable = false)
    private String enterpriseId;
    @Column(name = "request_id", length = 120, updatable = false)
    private String requestId;
    @Column(name = "trace_id", length = 120, updatable = false)
    private String traceId;
    @Column(name = "invocation_id", nullable = false, updatable = false, length = 160)
    private String invocationId;
    @Column(name = "attempt_id", updatable = false, length = 160)
    private String attemptId;
    @Column(name = "initiator", updatable = false, length = 200)
    private String initiator;
    @Column(name = "actor", updatable = false, length = 200)
    private String actor;
    @Column(name = "effective_subject", updatable = false, length = 200)
    private String effectiveSubject;
    @Column(name = "agent_instance_id", updatable = false, length = 160)
    private String agentInstanceId;
    @Column(name = "policy_version", updatable = false)
    private Integer policyVersion;
    @Column(name = "connection_revision", updatable = false)
    private Long connectionRevision;
    @Column(name = "permit_id", updatable = false, length = 160)
    private String permitId;
    @Column(name = "permit_jti", updatable = false, length = 200)
    private String permitJti;
    @Column(name = "tool_id", updatable = false, length = 160)
    private String toolId;
    @Column(name = "resource", updatable = false, length = 500)
    private String resource;
    @Column(name = "action", updatable = false, length = 120)
    private String action;
    @Column(name = "decision", updatable = false, length = 40)
    private String decision;
    @Column(name = "reason_code", updatable = false, length = 120)
    private String reasonCode;
    @Lob
    @Column(name = "detail_json", updatable = false)
    private String detailJson;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
    @Column(name = "previous_digest", updatable = false, length = 128)
    private String previousDigest;
    @Column(name = "event_digest", nullable = false, updatable = false, length = 128)
    private String eventDigest;

    protected AuditEventEntity() {
    }

    public AuditEventEntity(String eventId, String eventType, String enterpriseId, String invocationId,
                            Instant occurredAt, String detailJson, String previousDigest, String eventDigest) {
        if (enterpriseId == null || enterpriseId.isBlank()) {
            throw new IllegalArgumentException("enterpriseId is required for durable audit events");
        }
        this.eventId = eventId;
        this.eventType = eventType;
        this.enterpriseId = enterpriseId;
        this.invocationId = invocationId;
        this.occurredAt = occurredAt;
        this.detailJson = detailJson;
        this.previousDigest = previousDigest;
        this.eventDigest = eventDigest;
    }

    static AuditEventEntity fromView(AuditEventView view, ObjectMapper mapper) {
        AuditEventEntity entity = new AuditEventEntity(view.eventId(), view.eventType(), view.enterpriseId(),
                view.invocationId(), view.occurredAt(), writeDetail(view.detail(), mapper), view.previousDigest(), view.eventDigest());
        entity.requestId = view.requestId();
        entity.traceId = view.traceId();
        entity.attemptId = view.attemptId();
        entity.initiator = view.initiator();
        entity.actor = view.actor();
        entity.effectiveSubject = view.effectiveSubject();
        entity.agentInstanceId = view.agentInstanceId();
        entity.policyVersion = view.policyVersion();
        entity.connectionRevision = view.connectionRevision();
        entity.permitId = view.permitId();
        entity.permitJti = view.permitJti();
        entity.toolId = view.toolId();
        entity.resource = view.resource();
        entity.action = view.action();
        entity.decision = view.decision();
        entity.reasonCode = view.reasonCode();
        return entity;
    }

    private static String writeDetail(java.util.Map<String, Object> detail, ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(detail == null ? java.util.Map.of() : detail);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Audit detail cannot be serialized", ex);
        }
    }

    @PreUpdate
    void preventUpdate() {
        throw SummerException.withMessage(AuditLedgerErrorCode.EVENT_IMMUTABLE, "Audit events are append-only");
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getEnterpriseId() { return enterpriseId; }
    public String getInvocationId() { return invocationId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getEventDigest() { return eventDigest; }
}
