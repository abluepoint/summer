package com.abluepoint.summer.sample.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/** Append-only audit projection for Customer lifecycle operations. */
@Entity
@Table(name = "summer_audit_event")
class CustomerAuditEventEntity {
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
    @Column(name = "detail_json", updatable = false, columnDefinition = "text")
    private String detailJson;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected CustomerAuditEventEntity() {
    }

    CustomerAuditEventEntity(String eventId, String tenantId, String organizationId, String principal,
                             String resource, String action, String result, String requestId,
                             String detailJson, Instant occurredAt) {
        this.eventId = eventId;
        this.tenantId = tenantId;
        this.organizationId = organizationId;
        this.principal = principal;
        this.resource = resource;
        this.action = action;
        this.result = result;
        this.requestId = requestId;
        this.detailJson = detailJson;
        this.occurredAt = occurredAt;
    }

    @PreUpdate
    void rejectUpdate() {
        throw new IllegalStateException("Customer audit events are append-only");
    }

    String eventId() { return eventId; }
    String tenantId() { return tenantId; }
    String organizationId() { return organizationId; }
    String principal() { return principal; }
    String resource() { return resource; }
    String action() { return action; }
    String result() { return result; }
    String requestId() { return requestId; }
    String detailJson() { return detailJson; }
    Instant occurredAt() { return occurredAt; }
}
