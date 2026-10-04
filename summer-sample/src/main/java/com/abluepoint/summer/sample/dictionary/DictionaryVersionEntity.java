package com.abluepoint.summer.sample.dictionary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "summer_dictionary_version", uniqueConstraints = @UniqueConstraint(
        name = "uk_summer_dictionary_version_number", columnNames = {"tenant_id", "dictionary_code", "version_number"}))
class DictionaryVersionEntity {
    @Id
    @Column(name = "version_id", length = 36, nullable = false, updatable = false)
    private String id;
    @Column(name = "tenant_id", length = 80, nullable = false, updatable = false)
    private String tenantId;
    @Column(name = "dictionary_code", length = 80, nullable = false, updatable = false)
    private String dictionaryCode;
    @Column(name = "version_number", nullable = false, updatable = false)
    private long version;
    @Column(name = "status", length = 20, nullable = false)
    private String status;
    @Column(name = "snapshot_json", columnDefinition = "text", nullable = false, updatable = false)
    private String snapshotJson;
    @Column(name = "created_by", length = 128, updatable = false)
    private String createdBy;
    @Column(name = "request_id", length = 120, updatable = false)
    private String requestId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DictionaryVersionEntity() {}

    DictionaryVersionEntity(String id, String tenantId, String dictionaryCode, long version,
                            String status, String snapshotJson, String createdBy, String requestId) {
        this.id = id;
        this.tenantId = tenantId;
        this.dictionaryCode = dictionaryCode;
        this.version = version;
        this.status = status;
        this.snapshotJson = snapshotJson;
        this.createdBy = createdBy;
        this.requestId = requestId;
        this.createdAt = Instant.now();
    }

    String id() { return id; }
    String tenantId() { return tenantId; }
    String dictionaryCode() { return dictionaryCode; }
    long version() { return version; }
    String status() { return status; }
    String snapshotJson() { return snapshotJson; }
    String createdBy() { return createdBy; }
    String requestId() { return requestId; }
    Instant createdAt() { return createdAt; }
}
