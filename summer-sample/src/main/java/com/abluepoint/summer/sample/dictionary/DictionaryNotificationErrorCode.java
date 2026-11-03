package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Existing wire codes retained for compatibility. */
public enum DictionaryNotificationErrorCode implements ErrorCode {
    LAST_EVENT_ID("SUMMER_DICTIONARY_SSE_LAST_EVENT_ID", "Last-Event-ID must be a non-negative integer", ErrorCategory.INVALID_ARGUMENT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;
    DictionaryNotificationErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }
    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
