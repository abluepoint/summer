package com.abluepoint.summer.sample.authorization.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "summer_authorization_capability")
public class AuthorizationCapabilityEntity {
    @Id @Column(name = "capability_id", nullable = false, length = 96) private String capabilityId;
    @Column(name = "enterprise_id", length = 64) private String enterpriseId;
    @Column(name = "scope", nullable = false, length = 32) private String scope;
    @Column(name = "code", nullable = false, length = 200) private String code;
    @Column(name = "resource", nullable = false, length = 200) private String resource;
    @Column(name = "action", nullable = false, length = 64) private String action;
    @Column(name = "risk_level", nullable = false, length = 32) private String riskLevel;
    @Column(name = "status", nullable = false, length = 32) private String status;
    @Column(name = "version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected AuthorizationCapabilityEntity() {}
    public AuthorizationCapabilityEntity(String id, String enterpriseId, String scope, String code, String resource, String action, String riskLevel, String status, long version) {
        this.capabilityId = id; this.enterpriseId = enterpriseId; this.scope = scope; this.code = code; this.resource = resource; this.action = action; this.riskLevel = riskLevel; this.status = status; this.version = version; this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
    public void updateStatus(String value, long nextVersion) { status = value; version = nextVersion; updatedAt = Instant.now(); }
    public String capabilityId() { return capabilityId; } public String enterpriseId() { return enterpriseId; } public String scope() { return scope; } public String code() { return code; } public String resource() { return resource; } public String action() { return action; } public String riskLevel() { return riskLevel; } public String status() { return status; } public long version() { return version; }
}
