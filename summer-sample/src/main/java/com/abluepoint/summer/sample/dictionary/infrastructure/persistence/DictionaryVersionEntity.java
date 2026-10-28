package com.abluepoint.summer.sample.dictionary.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "summer_dictionary_version", uniqueConstraints = @UniqueConstraint(
        name = "uk_summer_dictionary_version_number", columnNames = {"tenant_id", "dictionary_code", "version_number"}))
public class DictionaryVersionEntity {
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

    public DictionaryVersionEntity(String id, String tenantId, String dictionaryCode, long version,
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

    public String id() { return id; }
    public String tenantId() { return tenantId; }
    public String dictionaryCode() { return dictionaryCode; }
    public long version() { return version; }
    public String status() { return status; }
    public String snapshotJson() { return snapshotJson; }
    public String createdBy() { return createdBy; }
    public String requestId() { return requestId; }
    public Instant createdAt() { return createdAt; }
}
