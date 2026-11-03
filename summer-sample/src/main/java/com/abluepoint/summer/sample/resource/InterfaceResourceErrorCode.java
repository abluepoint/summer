package com.abluepoint.summer.sample.resource;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Existing wire codes retained for compatibility. */
public enum InterfaceResourceErrorCode implements ErrorCode {
    REVISION_NOT_FOUND("SUMMER_RESOURCE_REVISION_NOT_FOUND", "Interface resource revision was not found", ErrorCategory.NOT_FOUND),
    HISTORY_INVALID("SUMMER_RESOURCE_HISTORY_INVALID", "Invalid interface resource history request", ErrorCategory.INVALID_ARGUMENT),
    PAGE_INVALID("SUMMER_RESOURCE_PAGE_INVALID", "Invalid interface resource page", ErrorCategory.INVALID_ARGUMENT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;
    InterfaceResourceErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }
    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
