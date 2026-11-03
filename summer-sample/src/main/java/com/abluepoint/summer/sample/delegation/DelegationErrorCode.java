package com.abluepoint.summer.sample.delegation;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Wire codes are retained for existing delegation clients. */
public enum DelegationErrorCode implements ErrorCode {
    CONFLICT("SUMMER_DELEGATION_CONFLICT", "The delegation mutation conflicts with current state", ErrorCategory.CONFLICT),
    INVALID("SUMMER_DELEGATION_INVALID", "The delegation request is invalid", ErrorCategory.INVALID_ARGUMENT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    DelegationErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
