package com.abluepoint.summer.sample.systemparameter;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Existing wire codes retained for compatibility. */
public enum SystemParameterErrorCode implements ErrorCode {
    INVALID("SUMMER_PARAMETER_INVALID", "System parameter validation failed", ErrorCategory.INVALID_ARGUMENT),
    CONFLICT("SUMMER_PARAMETER_CONFLICT", "System parameter key is already in use", ErrorCategory.CONFLICT),
    VERSION_CONFLICT("SUMMER_PARAMETER_VERSION_CONFLICT", "The system parameter was changed by another request", ErrorCategory.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;
    SystemParameterErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }
    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
