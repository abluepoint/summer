package com.abluepoint.summer.sample.bff;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

enum BffErrorCode implements ErrorCode {
    SESSION_REQUIRED("BFF_SESSION_REQUIRED", "Product session is missing or expired", ErrorCategory.UNAUTHORIZED),
    ENTERPRISE_CONTEXT_DENIED("BFF_ENTERPRISE_CONTEXT_DENIED", "Enterprise context is not available in the Product Session", ErrorCategory.FORBIDDEN),
    MCP_REQUEST_INVALID("BFF_MCP_REQUEST_INVALID", "connectionId and tool are required", ErrorCategory.INVALID_ARGUMENT),
    AGENT_ID_FORBIDDEN("BFF_AGENT_ID_FORBIDDEN", "agentId is derived by the server and cannot be supplied by the browser", ErrorCategory.INVALID_ARGUMENT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    BffErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    @Override public String code() { return code; }
    @Override public String defaultMessage() { return defaultMessage; }
    @Override public ErrorCategory category() { return category; }
}
