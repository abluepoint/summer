package com.abluepoint.summer.sample.release;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Keeps the published application code independent of its HTTP transport policy. */
enum ReleaseErrorCode implements ErrorCode {
    INVALID;

    @Override public String code() { return "IAM_RELEASE_INVALID"; }
    @Override public String defaultMessage() { return "The release request is invalid"; }
    @Override public ErrorCategory category() { return ErrorCategory.INVALID_ARGUMENT; }
}
