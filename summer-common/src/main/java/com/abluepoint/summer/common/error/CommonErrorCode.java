package com.abluepoint.summer.common.error;

public enum CommonErrorCode implements ErrorCode {
    INVALID_ARGUMENT("SUMMER_INVALID_ARGUMENT", "The request is invalid", ErrorCategory.INVALID_ARGUMENT),
    UNAUTHORIZED("SUMMER_UNAUTHORIZED", "Authentication is required", ErrorCategory.UNAUTHORIZED),
    FORBIDDEN("SUMMER_FORBIDDEN", "Access is denied", ErrorCategory.FORBIDDEN),
    NOT_FOUND("SUMMER_NOT_FOUND", "The requested resource was not found", ErrorCategory.NOT_FOUND),
    CONFLICT("SUMMER_CONFLICT", "The request conflicts with current state", ErrorCategory.CONFLICT),
    INTERNAL_ERROR("SUMMER_INTERNAL_ERROR", "An unexpected error occurred", ErrorCategory.INTERNAL_ERROR);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    CommonErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    @Override
    public ErrorCategory category() {
        return category;
    }
}
