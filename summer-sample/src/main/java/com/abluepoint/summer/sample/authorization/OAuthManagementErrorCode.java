package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Stable application codes retained for compatibility with existing clients. */
public enum OAuthManagementErrorCode implements ErrorCode {
    CLIENT_EXISTS("SUMMER_OAUTH_CLIENT_EXISTS", "Client ID already exists", ErrorCategory.CONFLICT),
    CLIENT_NOT_FOUND("SUMMER_OAUTH_CLIENT_NOT_FOUND", "Client was not found", ErrorCategory.NOT_FOUND),
    CLIENT_INVALID("SUMMER_OAUTH_CLIENT_INVALID", "Client metadata is invalid", ErrorCategory.INVALID_ARGUMENT),
    CONSENT_NOT_FOUND("SUMMER_OAUTH_CONSENT_NOT_FOUND", "Authorization consent was not found", ErrorCategory.NOT_FOUND);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    OAuthManagementErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
