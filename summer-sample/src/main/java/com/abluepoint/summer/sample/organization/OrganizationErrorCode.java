package com.abluepoint.summer.sample.organization;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Wire codes are retained for existing organization clients. */
public enum OrganizationErrorCode implements ErrorCode {
    CONFLICT("SUMMER_ORGANIZATION_CONFLICT", "The organization mutation conflicts with current state", ErrorCategory.CONFLICT),
    INVALID("SUMMER_ORGANIZATION_INVALID", "The organization request is invalid", ErrorCategory.INVALID_ARGUMENT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    OrganizationErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
