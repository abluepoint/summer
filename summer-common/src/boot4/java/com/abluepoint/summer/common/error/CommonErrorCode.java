package com.abluepoint.summer.common.error;

public enum CommonErrorCode implements ErrorCode {
    INVALID_ARGUMENT("SUMMER-400", "The request is invalid"),
    UNAUTHORIZED("SUMMER-401", "Authentication is required"),
    FORBIDDEN("SUMMER-403", "Access is denied"),
    NOT_FOUND("SUMMER-404", "The requested resource was not found"),
    CONFLICT("SUMMER-409", "The request conflicts with current state"),
    INTERNAL_ERROR("SUMMER-500", "An unexpected error occurred");

    private final String code;
    private final String defaultMessage;

    CommonErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
