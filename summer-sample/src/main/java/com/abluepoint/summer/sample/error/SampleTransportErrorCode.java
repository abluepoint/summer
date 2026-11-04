package com.abluepoint.summer.sample.error;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Shared legacy codes whose HTTP status is more specific than their category.
 * Existing SUMMER_ names are compatibility debt; new application codes use an application namespace.
 */
public enum SampleTransportErrorCode implements ErrorCode {
    PERMISSION_STALE("SAMPLE_PERMISSION_STALE", "The permission resource has changed", ErrorCategory.CONFLICT),
    PERMISSION_CONDITION_REQUIRED("SAMPLE_PERMISSION_CONDITION_REQUIRED", "If-Match is required", ErrorCategory.INVALID_ARGUMENT),
    PRECONDITION_FAILED("SUMMER_PRECONDITION_FAILED", "The resource has changed", ErrorCategory.CONFLICT),
    OAUTH_CLIENT_VERSION_CONFLICT("SUMMER_OAUTH_CLIENT_VERSION_CONFLICT", "Client version has changed; reload before retrying", ErrorCategory.CONFLICT),
    BFF_UNAVAILABLE("BFF_SUMMER_UNAVAILABLE", "Summer MCP adapter is not configured", ErrorCategory.INTERNAL_ERROR),
    BFF_UPSTREAM_ERROR("BFF_MCP_UPSTREAM_ERROR", "MCP execution is temporarily unavailable", ErrorCategory.INTERNAL_ERROR),
    SESSION_STORE_UNAVAILABLE("SUMMER_SESSION_STORE_UNAVAILABLE", "Shared session storage is unavailable", ErrorCategory.INTERNAL_ERROR),
    DEPENDENCY_UNAVAILABLE("SUMMER_DEPENDENCY_UNAVAILABLE", "The dependency is unavailable and no safe fallback is available", ErrorCategory.INTERNAL_ERROR),
    DICTIONARY_SSE_LIMIT("SUMMER_DICTIONARY_SSE_LIMIT", "Too many dictionary notification connections", ErrorCategory.CONFLICT),
    TASK_SSE_LIMIT("SUMMER_TASK_SSE_LIMIT", "Too many task progress connections", ErrorCategory.CONFLICT);

    private final String code;
    private final String defaultMessage;
    private final ErrorCategory category;

    SampleTransportErrorCode(String code, String defaultMessage, ErrorCategory category) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.category = category;
    }

    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
    public ErrorCategory category() { return category; }
}
