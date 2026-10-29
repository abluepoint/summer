package com.abluepoint.summer.sample.auditledger.domain;


public final class AuditLedgerConflictException extends RuntimeException {
    public AuditLedgerConflictException(String message) { super(message); }
}
