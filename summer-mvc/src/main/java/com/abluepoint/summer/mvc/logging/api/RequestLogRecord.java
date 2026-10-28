package com.abluepoint.summer.mvc.logging.api;

import java.util.Objects;

/**
 * Immutable snapshot of the HTTP metadata collected after a request completes.
 *
 * <p>The record intentionally contains no servlet objects or mutable request
 * state so that an output implementation cannot read the request stream or
 * change the business request.</p>
 */
public record RequestLogRecord(
        String requestId,
        String method,
        String path,
        int status,
        long durationMillis,
        String bodyPreview
) {

    public RequestLogRecord(
            String requestId,
            String method,
            String path,
            int status,
            long durationMillis
    ) {
        this(requestId, method, path, status, durationMillis, null);
    }

    public RequestLogRecord {
        requestId = requireText(requestId, "requestId");
        method = requireText(method, "method");
        path = requireText(path, "path");
        if (status < 100 || status > 999) {
            throw new IllegalArgumentException("status must be an HTTP status code");
        }
        if (durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis must not be negative");
        }
        if (bodyPreview != null && bodyPreview.isBlank()) {
            throw new IllegalArgumentException("bodyPreview must not be blank when present");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
