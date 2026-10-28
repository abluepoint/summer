package com.abluepoint.summer.sample.auditledger.application;


/**
 * Durable write seam for audit evidence. Implementations must atomically append
 * the immutable event and enqueue its publication message in one local transaction.
 */
@FunctionalInterface
public interface AuditLedgerPersistenceAdapter {
    void appendAndEnqueue(AuditEventView event, String idempotencyKey, String fingerprint);
}
