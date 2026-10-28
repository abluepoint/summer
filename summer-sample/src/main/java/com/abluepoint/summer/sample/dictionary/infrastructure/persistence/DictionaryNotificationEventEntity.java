package com.abluepoint.summer.sample.dictionary.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "summer_dictionary_notification_event")
public class DictionaryNotificationEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 80)
    private String tenantId;
    @Column(name = "dictionary_code", nullable = false, updatable = false, length = 80)
    private String dictionaryCode;
    @Column(name = "event_type", nullable = false, updatable = false, length = 80)
    private String eventType;
    @Column(name = "version_number", nullable = false, updatable = false)
    private long version;
    @Column(name = "request_id", updatable = false, length = 120)
    private String requestId;
    @Column(name = "payload_json", nullable = false, updatable = false, columnDefinition = "text")
    private String payloadJson;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DictionaryNotificationEventEntity() {}

    public DictionaryNotificationEventEntity(String tenantId, String dictionaryCode, String eventType,
                                       long version, String requestId, String payloadJson) {
        this.tenantId = tenantId;
        this.dictionaryCode = dictionaryCode;
        this.eventType = eventType;
        this.version = version;
        this.requestId = requestId;
        this.payloadJson = payloadJson;
        this.createdAt = Instant.now();
    }

    public void assignEventId(Long eventId) { this.eventId = eventId; }

    public Long eventId() { return eventId; }
    public String tenantId() { return tenantId; }
    public String dictionaryCode() { return dictionaryCode; }
    public String eventType() { return eventType; }
    public long version() { return version; }
    public String requestId() { return requestId; }
    public String payloadJson() { return payloadJson; }
    public Instant createdAt() { return createdAt; }
}
