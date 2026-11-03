package com.abluepoint.summer.sample.member;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Stable failures for member operations. */
public enum MemberErrorCode implements ErrorCode {
    CONFLICT("SUMMER_MEMBER_CONFLICT", "Subject already has a membership in this enterprise", ErrorCategory.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;
    MemberErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code; this.defaultMessage = defaultMessage; this.category = category;
    }
    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
