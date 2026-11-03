package com.abluepoint.summer.sample.enterprise;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Stable legacy failures shared by enterprise directory operations. */
public enum DirectoryErrorCode implements ErrorCode {
    ENTERPRISE_NOT_FOUND("SUMMER_ENTERPRISE_NOT_FOUND", "Enterprise not found", ErrorCategory.NOT_FOUND),
    INVALID_ENTERPRISE_ACTION("SUMMER_INVALID_ENTERPRISE_ACTION", "Invalid enterprise action", ErrorCategory.INVALID_ARGUMENT),
    IDEMPOTENCY_CONFLICT("SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency-Key was already used for another mutation", ErrorCategory.CONFLICT),
    INVALID_REVIEW("SUMMER_INVALID_REVIEW", "Invalid enterprise review", ErrorCategory.INVALID_ARGUMENT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;
    DirectoryErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code; this.defaultMessage = defaultMessage; this.category = category;
    }
    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
