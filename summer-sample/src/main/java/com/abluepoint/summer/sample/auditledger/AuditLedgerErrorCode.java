package com.abluepoint.summer.sample.auditledger;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Stable audit failures consumed by the ledger and its execution boundary. */
public enum AuditLedgerErrorCode implements ErrorCode {
    INVALID("IAM_AUDIT_INVALID", "Audit request is invalid", ErrorCategory.INVALID_ARGUMENT),
    CONFLICT("IAM_AUDIT_CONFLICT", "Audit state conflicts with the request", ErrorCategory.CONFLICT),
    IDEMPOTENCY_CONFLICT("SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency key reused", ErrorCategory.CONFLICT),
    EVENT_IMMUTABLE("IAM_AUDIT_EVENT_IMMUTABLE", "Event is immutable", ErrorCategory.CONFLICT),
    CONTRADICTORY_RECEIPT("IAM_AUDIT_CONTRADICTORY_RECEIPT", "Conflicting result digest", ErrorCategory.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    AuditLedgerErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public ErrorCategory category() { return category; }
}
