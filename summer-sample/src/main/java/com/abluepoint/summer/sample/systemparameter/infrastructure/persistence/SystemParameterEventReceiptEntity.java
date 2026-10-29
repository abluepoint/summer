package com.abluepoint.summer.sample.systemparameter.infrastructure.persistence;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterEventReceiptStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "summer_system_parameter_event_receipt")
public class SystemParameterEventReceiptEntity {
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

    public SystemParameterEventReceiptEntity(String eventId, Instant now) {
        this.eventId = eventId;
        this.status = SystemParameterEventReceiptStatus.PROCESSED;
        this.updatedAt = now;
    }

    public String eventId() { return eventId; }
    public SystemParameterEventReceiptStatus status() { return status; }
    public int attempts() { return attempts; }

    public void markProcessed() {
        status = SystemParameterEventReceiptStatus.PROCESSED;
        lastError = null;
        updatedAt = Instant.now();
    }

    public void retry(String error, boolean deadLetter) {
        attempts++;
        status = deadLetter ? SystemParameterEventReceiptStatus.DEAD_LETTER : SystemParameterEventReceiptStatus.RETRY;
        lastError = error == null || error.isBlank() ? "consumer_failed" : error.substring(0, Math.min(500, error.length()));
        updatedAt = Instant.now();
    }
}
