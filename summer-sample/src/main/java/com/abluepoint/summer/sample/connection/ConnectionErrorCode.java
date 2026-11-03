package com.abluepoint.summer.sample.connection;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Existing application codes retained for API compatibility. */
enum ConnectionErrorCode implements ErrorCode {
    INVALID("SUMMER_CONNECTION_INVALID", "Connection request is invalid", ErrorCategory.INVALID_ARGUMENT),
    CONFLICT("SUMMER_CONNECTION_CONFLICT", "Connection conflicts with the request", ErrorCategory.CONFLICT),
    IDEMPOTENCY_CONFLICT("SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency key reused", ErrorCategory.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    ConnectionErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
