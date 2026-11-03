package com.abluepoint.summer.sample.realtime;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Stable application codes retained for compatibility with existing clients. */
public enum RealtimeErrorCode implements ErrorCode {
    INVALID("SUMMER_MESSAGE_INVALID", "Message is invalid", ErrorCategory.INVALID_ARGUMENT),
    CURSOR_INVALID("SUMMER_MESSAGE_CURSOR_INVALID", "Message cursor must be a non-negative integer", ErrorCategory.INVALID_ARGUMENT),
    CONNECTION_LIMIT("SAMPLE_REALTIME_CONNECTION_LIMIT", "connection limit", ErrorCategory.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    RealtimeErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
