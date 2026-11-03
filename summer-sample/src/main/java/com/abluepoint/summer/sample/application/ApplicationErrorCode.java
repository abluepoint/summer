package com.abluepoint.summer.sample.application;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Existing application codes retained for API compatibility. */
enum ApplicationErrorCode implements ErrorCode {
    INVALID("SUMMER_APPLICATION_INVALID", "Application request is invalid", ErrorCategory.INVALID_ARGUMENT),
    CONFLICT("SUMMER_APPLICATION_CONFLICT", "Application conflicts with the request", ErrorCategory.CONFLICT),
    IDEMPOTENCY_CONFLICT("SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency key reused", ErrorCategory.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    ApplicationErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
