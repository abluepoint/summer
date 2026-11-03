package com.abluepoint.summer.sample.agent;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Existing application codes retained for API compatibility. */
enum AgentErrorCode implements ErrorCode {
    INVALID("SUMMER_AGENT_INVALID", "Agent request is invalid", ErrorCategory.INVALID_ARGUMENT),
    CONFLICT("SUMMER_AGENT_CONFLICT", "Agent conflicts with the request", ErrorCategory.CONFLICT),
    IDEMPOTENCY_CONFLICT("SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency key reused", ErrorCategory.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    AgentErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
