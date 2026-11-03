package com.abluepoint.summer.sample.mcpauthorization;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Stable application codes retained for compatibility with existing clients. */
public enum McpAuthorizationErrorCode implements ErrorCode {
    INVALID("SUMMER_MCP_AUTH_INVALID", "Execution decision is invalid", ErrorCategory.INVALID_ARGUMENT),
    CONFLICT("SUMMER_MCP_AUTH_CONFLICT", "Execution permit conflicts with the request", ErrorCategory.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    McpAuthorizationErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
