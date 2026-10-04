package com.abluepoint.summer.sample.systemparameter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "summer_system_parameter_event_receipt")
class SystemParameterEventReceiptEntity {
    @Id
    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SystemParameterEventReceiptStatus status;
    @Column(name = "attempts", nullable = false)
    private int attempts;
    @Column(name = "last_error", length = 500)
    private String lastError;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SystemParameterEventReceiptEntity() {}

    SystemParameterEventReceiptEntity(String eventId, Instant now) {
        this.eventId = eventId;
        this.status = SystemParameterEventReceiptStatus.PROCESSED;
        this.updatedAt = now;
    }

    String eventId() { return eventId; }
    SystemParameterEventReceiptStatus status() { return status; }
    int attempts() { return attempts; }

    void markProcessed() {
        status = SystemParameterEventReceiptStatus.PROCESSED;
        lastError = null;
        updatedAt = Instant.now();
    }

    void retry(String error, boolean deadLetter) {
        attempts++;
        status = deadLetter ? SystemParameterEventReceiptStatus.DEAD_LETTER : SystemParameterEventReceiptStatus.RETRY;
        lastError = error == null || error.isBlank() ? "consumer_failed" : error.substring(0, Math.min(500, error.length()));
        updatedAt = Instant.now();
    }
}

enum SystemParameterEventReceiptStatus { PROCESSED, RETRY, DEAD_LETTER }
