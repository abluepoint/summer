package com.abluepoint.summer.sample.realtime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "summer_realtime_message", uniqueConstraints = @UniqueConstraint(
        name = "uk_realtime_message_client", columnNames = {"enterprise_id", "sender_principal", "client_message_id"}))
class RealtimeMessageEntity {
    @Id
    @Column(name = "message_id", nullable = false, updatable = false, length = 36)
    private String messageId;
    @Column(name = "enterprise_id", nullable = false, updatable = false, length = 64)
    private String enterpriseId;
    @Column(name = "sender_principal", nullable = false, updatable = false, length = 128)
    private String sender;
    @Column(name = "client_message_id", nullable = false, updatable = false, length = 128)
    private String clientMessageId;
    @Column(name = "recipient_principal", updatable = false, length = 128)
    private String recipient;
    @Column(name = "payload_json", nullable = false, updatable = false, columnDefinition = "text")
    private String payloadJson;
    @Column(name = "sequence_no", nullable = false, updatable = false)
    private long sequence;
    @Column(name = "status", nullable = false, length = 32)
    private String status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;
    @Column(name = "expires_at")
    private Instant expiresAt;

    protected RealtimeMessageEntity() {}

    RealtimeMessageEntity(String messageId, String enterpriseId, String sender, String clientMessageId,
                          String recipient, String payloadJson, long sequence, Instant expiresAt) {
        this.messageId = messageId; this.enterpriseId = enterpriseId; this.sender = sender;
        this.clientMessageId = clientMessageId; this.recipient = recipient; this.payloadJson = payloadJson;
        this.sequence = sequence; this.status = "PENDING"; this.createdAt = Instant.now(); this.expiresAt = expiresAt;
    }

    String messageId() { return messageId; }
    String enterpriseId() { return enterpriseId; }
    String sender() { return sender; }
    String clientMessageId() { return clientMessageId; }
    String recipient() { return recipient; }
    String payloadJson() { return payloadJson; }
    long sequence() { return sequence; }
    String status() { return status; }
    Instant createdAt() { return createdAt; }
    Instant acknowledgedAt() { return acknowledgedAt; }
    Instant expiresAt() { return expiresAt; }

    void acknowledge() { if (!"ACKED".equals(status)) { status = "ACKED"; acknowledgedAt = Instant.now(); } }
}
