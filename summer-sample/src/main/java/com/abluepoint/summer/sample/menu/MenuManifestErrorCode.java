package com.abluepoint.summer.sample.menu;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Stable application codes retained for compatibility with existing clients. */
public enum MenuManifestErrorCode implements ErrorCode {
    INVALID("SUMMER_MENU_MANIFEST_INVALID", "Menu manifest is invalid", ErrorCategory.INVALID_ARGUMENT),
    CONFLICT("SUMMER_MENU_MANIFEST_CONFLICT", "Menu manifest conflicts with the request", ErrorCategory.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    MenuManifestErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
