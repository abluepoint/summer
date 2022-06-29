package com.abluepoint.summer.mvc.domain;

import org.springframework.lang.Nullable;

public enum ReturnCode {
    OK(0, "0.system.message.ok"),
    PENDING(1, "1.system.message.pending"),
    FORBIDDEN(403, "403.system.message.forbidden"),
    UNAUTHORIZED(401,"401.system.message.unauthorized"),
    LOGIN_FAILED(1001,"1001.system.message.login_failed"),
    ;

    private final int value;
    private final String message;

    private static final ReturnCode[] VALUES;

    static {
        VALUES = values();
    }

    ReturnCode(int value, String message) {
        this.value = value;
        this.message = message;
    }

    public int value() {
        return value;
    }

    public String message() {
        return message;
    }

    public static ReturnCode valueOf(int statusCode) {
        ReturnCode returnCode = resolve(statusCode);
        if (returnCode == null) {
            throw new IllegalArgumentException("No matching constant for [" + statusCode + "]");
        }
        return returnCode;
    }

    @Nullable
    public static ReturnCode resolve(int value) {
        // Use cached VALUES instead of values() to prevent array allocation.
        for (ReturnCode rc : VALUES) {
            if (rc.value == value) {
                return rc;
            }
        }
        return null;
    }
}