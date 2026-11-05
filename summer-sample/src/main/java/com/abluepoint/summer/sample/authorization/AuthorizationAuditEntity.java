package com.abluepoint.summer.sample.authorization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "summer_authorization_audit")
class AuthorizationAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id") private Long auditId;
    @Column(name = "enterprise_id", nullable = false, length = 96) private String enterpriseId;
    @Column(name = "username", nullable = false, length = 128) private String username;
    @Column(name = "resource_id", nullable = false, length = 256) private String resourceId;
    @Column(name = "action", nullable = false, length = 64) private String action;
    @Column(name = "principal", nullable = false, length = 128) private String principal;
    @Column(name = "permission_code", nullable = false, length = 200) private String permissionCode;
    @Column(name = "decision", nullable = false, length = 16) private String decision;
    @Column(name = "reason_code", nullable = false, length = 64) private String reasonCode;
    @Column(name = "request_id", length = 128) private String requestId;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    @Column(name = "authorization_resource_id", length = 96) private String authorizationResourceId;
    @Column(name = "authorization_revision") private Long authorizationRevision;
    protected AuthorizationAuditEntity() {}
    void evidence(String resourceId, long revision) {
        this.authorizationResourceId = resourceId; this.authorizationRevision = revision;
    }

    AuthorizationAuditEntity(String enterpriseId, String resourceId, String action, String principal,
                             String requestId, String permissionCode, String decision, String reasonCode) {
        this.enterpriseId = safe(enterpriseId);
        this.resourceId = safe(resourceId);
        this.action = action;
        this.principal = safe(principal);
        this.username = resourceId != null && resourceId.startsWith("user:") ? resourceId.substring(5) : this.principal;
        this.requestId = safe(requestId);
        this.permissionCode = safe(permissionCode);
        this.decision = safe(decision);
        this.reasonCode = safe(reasonCode);
        this.occurredAt = Instant.now();
    }

    Map<String, Object> view() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", action);
        result.put("requestId", safe(requestId));
        result.put("principal", principal);
        result.put("at", occurredAt.toString());
        boolean fullDomain = enterpriseId.startsWith("ENTERPRISE:") || enterpriseId.equals("PLATFORM:platform");
        result.put("domainProvenance", fullDomain ? "FULL_DOMAIN_KEY" : "LEGACY_UNRESOLVED");
        if (fullDomain) result.put("domainKey", enterpriseId);
        if (!permissionCode.isEmpty()) result.put("permissionCode", permissionCode);
        if (!decision.isEmpty()) result.put("decision", decision);
        if (!reasonCode.isEmpty()) result.put("reasonCode", reasonCode);
        if (authorizationResourceId != null) result.put("resourceId", authorizationResourceId);
        if (authorizationRevision != null) result.put("authorizationRevision", authorizationRevision);
        return Map.copyOf(result);
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
