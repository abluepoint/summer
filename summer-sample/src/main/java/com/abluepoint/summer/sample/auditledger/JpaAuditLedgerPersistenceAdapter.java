package com.abluepoint.summer.sample.auditledger;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Production adapter enabled with the {@code audit-durable} Spring profile. */
@Component
@Profile("audit-durable")
class JpaAuditLedgerPersistenceAdapter implements AuditLedgerPersistenceAdapter {
    private final AuditEventStore eventStore;
    private final AuditOutboxStore outboxStore;
    private final ObjectMapper objectMapper;

    JpaAuditLedgerPersistenceAdapter(AuditEventStore eventStore, AuditOutboxStore outboxStore,
                                     ObjectMapper objectMapper) {
        this.eventStore = eventStore;
        this.outboxStore = outboxStore;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void appendAndEnqueue(AuditEventView event, String idempotencyKey, String fingerprint) {
        AuditEventEntity persisted = eventStore.append(AuditEventEntity.fromView(event, objectMapper));
        try {
            String payload = objectMapper.writeValueAsString(java.util.Map.of(
                    "eventId", persisted.getEventId(),
                    "enterpriseId", persisted.getEnterpriseId(),
                    "eventType", persisted.getEventType(),
                    "invocationId", persisted.getInvocationId(),
                    "eventDigest", persisted.getEventDigest(),
                    "idempotencyKey", idempotencyKey,
                    "fingerprint", fingerprint));
            outboxStore.enqueue(new AuditOutboxEntity(persisted.getEventId(), persisted.getEnterpriseId(), payload, Instant.now()));
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Audit outbox payload cannot be serialized", ex);
        }
    }
}
