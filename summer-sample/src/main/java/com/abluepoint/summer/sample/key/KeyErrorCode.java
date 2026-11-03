package com.abluepoint.summer.sample.key;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Existing key-management and deployment-reference diagnostics. */
public enum KeyErrorCode implements ErrorCode {
    ROTATION_INVALID("SUMMER_KEY_ROTATION_INVALID", "Key rotation request is invalid", ErrorCategory.INVALID_ARGUMENT),
    REFERENCE_INVALID("SUMMER_KEY_REFERENCE_INVALID", "Signing key reference is invalid", ErrorCategory.INVALID_ARGUMENT),
    REFERENCE_UNAVAILABLE("SUMMER_KEY_REFERENCE_UNAVAILABLE", "Signing key reference is unavailable", ErrorCategory.INTERNAL_ERROR);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    KeyErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public ErrorCategory category() { return category; }
}
