package com.abluepoint.summer.sample.auditledger.domain;


public final class AuditLedgerIdempotencyConflictException extends RuntimeException {
    public AuditLedgerIdempotencyConflictException(String message) { super(message); }
}

