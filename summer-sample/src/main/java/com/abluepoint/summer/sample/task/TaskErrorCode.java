package com.abluepoint.summer.sample.task;

import com.abluepoint.summer.common.error.ErrorCategory;
import com.abluepoint.summer.common.error.ErrorCode;

/** Existing task wire codes and the internal timeout discriminator. */
enum TaskErrorCode implements ErrorCode {
    INVALID("SUMMER_TASK_INVALID", "Task request is invalid", ErrorCategory.INVALID_ARGUMENT),
    TYPE_UNTRUSTED("SUMMER_TASK_TYPE_UNTRUSTED", "Task type is not trusted", ErrorCategory.INVALID_ARGUMENT),
    CONFLICT("SUMMER_TASK_CONFLICT", "Task conflicts with the current state", ErrorCategory.CONFLICT),
    IDEMPOTENCY_CONFLICT("SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency-Key was already used for another task trigger", ErrorCategory.CONFLICT),
    IDEMPOTENCY_REQUIRED("SUMMER_IDEMPOTENCY_REQUIRED", "Idempotency-Key is required", ErrorCategory.INVALID_ARGUMENT),
    SSE_LAST_EVENT_ID("SUMMER_TASK_SSE_LAST_EVENT_ID", "Last-Event-ID must be a non-negative integer", ErrorCategory.INVALID_ARGUMENT),
    TIMED_OUT("SAMPLE_TASK_TIMED_OUT", "Task execution timed out", ErrorCategory.INTERNAL_ERROR);

    private final String code;
    private final String message;
    private final ErrorCategory category;

    TaskErrorCode(String code, String message, ErrorCategory category) {
        this.code = code;
        this.message = message;
        this.category = category;
    }

    public String code() { return code; }
    public String defaultMessage() { return message; }
    public ErrorCategory category() { return category; }
}
