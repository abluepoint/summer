package com.abluepoint.summer.sample.systemparameter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "summer_system_parameter_version", uniqueConstraints =
        @UniqueConstraint(name = "uk_summer_parameter_version_idempotency",
                columnNames = {"parameter_key", "idempotency_key"}))
class SystemParameterVersionEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 80)
    private String tenantId;
    @Column(name = "parameter_key", nullable = false, length = 120)
    private String parameterKey;
    @Column(name = "revision", nullable = false)
    private long revision;
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private SystemParameterReleaseState state;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text")
    private String snapshotJson;
    @Column(name = "base_version", nullable = false)
    private long baseVersion;
    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;
    @Column(name = "principal", nullable = false, length = 128)
    private String principal;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SystemParameterVersionEntity() {
    }

    SystemParameterVersionEntity(String parameterKey, long revision, SystemParameterReleaseState state,
                                  String snapshotJson, long baseVersion, String idempotencyKey,
                                  String principal) {
        this.id = UuidV7.next();
        this.tenantId = SystemParameterService.TENANT_ID;
        this.parameterKey = parameterKey;
        this.revision = revision;
        this.state = state;
        this.snapshotJson = snapshotJson;
        this.baseVersion = baseVersion;
        this.idempotencyKey = idempotencyKey;
        this.principal = principal == null || principal.isBlank() ? "system" : principal;
        this.createdAt = Instant.now();
    }

    String id() { return id; }
    String tenantId() { return tenantId; }
    String parameterKey() { return parameterKey; }
    long revision() { return revision; }
    SystemParameterReleaseState state() { return state; }
    String snapshotJson() { return snapshotJson; }
    long baseVersion() { return baseVersion; }
    String idempotencyKey() { return idempotencyKey; }
    String principal() { return principal; }
    Instant createdAt() { return createdAt; }
}
