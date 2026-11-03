package com.abluepoint.summer.sample.authorization.policy;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Stable application codes retained for compatibility with existing clients. */
public enum PolicyErrorCode implements ErrorCode {
    INVALID("SUMMER_POLICY_INVALID", "Policy request is invalid", ErrorCategory.INVALID_ARGUMENT),
    CONFLICT("SUMMER_POLICY_CONFLICT", "Policy conflicts with the request", ErrorCategory.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    PolicyErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
