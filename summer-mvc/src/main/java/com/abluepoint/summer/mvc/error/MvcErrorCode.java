package com.abluepoint.summer.mvc.error;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

public enum MvcErrorCode implements ErrorCode {
    REQUEST_BODY_TOO_LARGE;

    public String code() { return "SUMMER_REQUEST_BODY_TOO_LARGE"; }
    public String defaultMessage() { return "Request body too large"; }
    public ErrorCategory category() { return ErrorCategory.INVALID_ARGUMENT; }
}
