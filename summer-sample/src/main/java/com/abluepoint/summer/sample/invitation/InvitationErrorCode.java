package com.abluepoint.summer.sample.invitation;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Stable failures for invitation operations. */
public enum InvitationErrorCode implements ErrorCode {
    UNUSABLE("SUMMER_INVITATION_UNUSABLE", "Invitation is not usable", ErrorCategory.CONFLICT),
    CONTEXT_MISMATCH("SUMMER_INVITATION_CONTEXT_MISMATCH", "Invitation enterprise context does not match token", ErrorCategory.CONFLICT),
    INVALID("SUMMER_INVITATION_INVALID", "Invalid invitation", ErrorCategory.INVALID_ARGUMENT),
    JOIN_APPLICATION_CONFLICT("SUMMER_JOIN_APPLICATION_CONFLICT", "Join application cannot be changed in current state", ErrorCategory.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;
    InvitationErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code; this.defaultMessage = defaultMessage; this.category = category;
    }
    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
