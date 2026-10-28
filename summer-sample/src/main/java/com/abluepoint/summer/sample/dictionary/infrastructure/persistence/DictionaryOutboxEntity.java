package com.abluepoint.summer.sample.dictionary.infrastructure.persistence;

import com.abluepoint.summer.sample.dictionary.application.DictionaryOutboxStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "summer_dictionary_outbox")
public class DictionaryOutboxEntity {
    @Id
    @Column(name = "event_id", length = 36, nullable = false, updatable = false)
    private String eventId;
    @Column(name = "tenant_id", length = 80, nullable = false, updatable = false)
    private String tenantId;
    @Column(name = "dictionary_code", length = 80, nullable = false, updatable = false)
    private String dictionaryCode;
    @Column(name = "event_type", length = 80, nullable = false, updatable = false)
    private String eventType;
    @Column(name = "version_number", nullable = false, updatable = false)
    private long version;
    @Column(name = "request_id", length = 120, updatable = false)
    private String requestId;
    @Column(name = "idempotency_key", length = 255, updatable = false)
    private String idempotencyKey;
    @Lob
    @Column(name = "payload_json", nullable = false, updatable = false)
    private String payloadJson;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private DictionaryOutboxStatus status = DictionaryOutboxStatus.PENDING;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DictionaryOutboxEntity() {}

    public DictionaryOutboxEntity(String eventId, String tenantId, String dictionaryCode, String eventType,
                           long version, String requestId, String idempotencyKey, String payloadJson) {
        this.eventId = eventId;
        this.tenantId = tenantId;
        this.dictionaryCode = dictionaryCode;
        this.eventType = eventType;
        this.version = version;
        this.requestId = requestId;
        this.idempotencyKey = idempotencyKey;
        this.payloadJson = payloadJson;
        this.createdAt = Instant.now();
    }

    public String eventId() { return eventId; }
    public String tenantId() { return tenantId; }
    public String dictionaryCode() { return dictionaryCode; }
    public String eventType() { return eventType; }
    public long version() { return version; }
    public String requestId() { return requestId; }
    public String idempotencyKey() { return idempotencyKey; }
    public String payloadJson() { return payloadJson; }
    public DictionaryOutboxStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
}
