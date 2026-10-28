package com.abluepoint.summer.sample.authorization.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "summer_data_scope_policy_audit")
public class DataScopePolicyAuditEntity {
    @Id @Column(name = "audit_id", nullable = false, length = 96) private String auditId;
    @Column(name = "enterprise_id", nullable = false, length = 64) private String enterpriseId;
    @Column(name = "resource", nullable = false, length = 160) private String resource;
    @Column(name = "version", nullable = false) private long version;
    @Column(name = "checksum", nullable = false, length = 64) private String checksum;
    @Column(name = "action", nullable = false, length = 64) private String action;
    @Column(name = "principal", length = 128) private String principal;
    @Column(name = "request_id", length = 96) private String requestId;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    protected DataScopePolicyAuditEntity() {}

    public DataScopePolicyAuditEntity(String auditId, String enterpriseId, String resource, long version,
                               String checksum, String action, String principal, String requestId) {
        this.auditId = auditId;
        this.enterpriseId = enterpriseId;
        this.resource = resource;
        this.version = version;
        this.checksum = checksum;
        this.action = action;
        this.principal = principal;
        this.requestId = requestId;
        this.occurredAt = Instant.now();
    }

    public String auditId() { return auditId; }
    public String enterpriseId() { return enterpriseId; }
    public String resource() { return resource; }
    public long version() { return version; }
    public String checksum() { return checksum; }
    public String action() { return action; }
    public String principal() { return principal; }
    public String requestId() { return requestId; }
    public Instant occurredAt() { return occurredAt; }
}
