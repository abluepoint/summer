package com.abluepoint.summer.sample.auditledger.infrastructure;


import java.util.Optional;

/** Durable append-only event boundary. The sample service may continue using its in-memory implementation. */
public interface AuditEventStore {
    AuditEventEntity append(AuditEventEntity event);

    Optional<AuditEventEntity> findByEventId(String eventId);
}
