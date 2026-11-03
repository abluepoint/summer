package com.abluepoint.summer.sample.physicalconnection;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

enum PhysicalExecutionErrorCode implements ErrorCode {
    INVALID("SUMMER_EXECUTION_INVALID", "Execution request is invalid", ErrorCategory.INVALID_ARGUMENT),
    CONFLICT("SUMMER_EXECUTION_CONFLICT", "Execution conflicts with the request", ErrorCategory.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    PhysicalExecutionErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
