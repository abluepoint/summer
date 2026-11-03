package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Stable application codes retained for compatibility with existing clients. */
public enum AuthorizationErrorCode implements ErrorCode {
    INVALID("SUMMER_AUTHORIZATION_INVALID", "Authorization request is invalid", ErrorCategory.INVALID_ARGUMENT),
    CONFLICT("SUMMER_AUTHORIZATION_CONFLICT", "Authorization resource conflicts with the request", ErrorCategory.CONFLICT),
    IDEMPOTENCY_CONFLICT("SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency key reused", ErrorCategory.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    AuthorizationErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
