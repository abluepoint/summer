package com.abluepoint.summer.sample.auditledger;

final class AuditLedgerValidationException extends RuntimeException {
    AuditLedgerValidationException(String message) { super(message); }
}

final class AuditLedgerConflictException extends RuntimeException {
    AuditLedgerConflictException(String message) { super(message); }
}

final class AuditLedgerIdempotencyConflictException extends RuntimeException {
    AuditLedgerIdempotencyConflictException(String message) { super(message); }
}

final class AuditEventImmutableException extends RuntimeException {
    AuditEventImmutableException(String message) { super(message); }
}

final class AuditContradictoryReceiptException extends RuntimeException {
    AuditContradictoryReceiptException(String message) { super(message); }
}
