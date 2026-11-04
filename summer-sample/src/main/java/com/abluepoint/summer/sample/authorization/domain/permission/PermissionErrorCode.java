package com.abluepoint.summer.sample.authorization.domain.permission;

import com.abluepoint.summer.common.error.*;

public enum PermissionErrorCode implements ErrorCode {
    INVALID(ErrorCategory.INVALID_ARGUMENT),
    NOT_FOUND(ErrorCategory.NOT_FOUND),
    CONFLICT(ErrorCategory.CONFLICT);
    private final ErrorCategory category;

    PermissionErrorCode(ErrorCategory category) {
        this.category = category;
    }

    public String code() {
        return "SAMPLE_PERMISSION_" + name();
    }

    public String defaultMessage() {
        return "Permission management request failed";
    }

    public ErrorCategory category() {
        return category;
    }
}
