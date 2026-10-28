package com.abluepoint.summer.sample.customer.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/** Append-only audit projection for Customer lifecycle operations. */
@Entity
@Table(name = "summer_audit_event")
public class CustomerAuditEventEntity {
    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 80)
    private String tenantId;
    @Column(name = "organization_id", updatable = false, length = 80)
    private String organizationId;
    @Column(name = "principal", nullable = false, updatable = false, length = 200)
    private String principal;
    @Column(name = "resource", nullable = false, updatable = false, length = 240)
    private String resource;
    @Column(name = "action", nullable = false, updatable = false, length = 80)
    private String action;
    @Column(name = "result", nullable = false, updatable = false, length = 40)
    private String result;
    @Column(name = "request_id", updatable = false, length = 120)
    private String requestId;
    @Column(name = "trace_id", updatable = false, length = 32)
    private String traceId;
    @Column(name = "detail_json", updatable = false, columnDefinition = "text")
    private String detailJson;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected CustomerAuditEventEntity() {
    }

    public CustomerAuditEventEntity(String eventId, String tenantId, String organizationId, String principal,
                             String resource, String action, String result, String requestId,
                             String traceId, String detailJson, Instant occurredAt) {
        this.eventId = eventId;
        this.tenantId = tenantId;
        this.organizationId = organizationId;
        this.principal = principal;
        this.resource = resource;
        this.action = action;
        this.result = result;
        this.requestId = requestId;
        this.traceId = traceId;
        this.detailJson = detailJson;
        this.occurredAt = occurredAt;
    }

    @PreUpdate
    void rejectUpdate() {
        throw new IllegalStateException("Customer audit events are append-only");
    }

    public String eventId() { return eventId; }
    public String tenantId() { return tenantId; }
    public String organizationId() { return organizationId; }
    public String principal() { return principal; }
    public String resource() { return resource; }
    public String action() { return action; }
    public String result() { return result; }
    public String requestId() { return requestId; }
    public String traceId() { return traceId; }
    public String detailJson() { return detailJson; }
    public Instant occurredAt() { return occurredAt; }
}
