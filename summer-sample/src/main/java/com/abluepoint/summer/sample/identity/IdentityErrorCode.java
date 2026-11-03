package com.abluepoint.summer.sample.identity;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Existing identity API codes retained during the exception migration. */
public enum IdentityErrorCode implements ErrorCode {
    USER_CONFLICT("SUMMER_USER_CONFLICT", "Username already exists", ErrorCategory.CONFLICT),
    SESSION_REFERENCE_INVALID("SUMMER_SESSION_REFERENCE_INVALID", "The session reference must be a 64-character hexadecimal value", ErrorCategory.INVALID_ARGUMENT),
    SESSION_NOT_FOUND("SUMMER_SESSION_NOT_FOUND", "The session was not found", ErrorCategory.NOT_FOUND),
    SESSION_REVOKED("SUMMER_SESSION_REVOKED", "The server session has been revoked", ErrorCategory.UNAUTHORIZED);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    IdentityErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
