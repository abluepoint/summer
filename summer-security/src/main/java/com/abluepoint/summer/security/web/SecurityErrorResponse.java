package com.abluepoint.summer.security.web;

import com.abluepoint.summer.common.response.UnwrappedResponse;

import java.time.Instant;

public record SecurityErrorResponse(
        boolean success,
        Error error,
        String requestId,
        Instant timestamp
) implements UnwrappedResponse {
    public static SecurityErrorResponse of(String code, String message, String requestId) {
        return new SecurityErrorResponse(false, new Error(code, message), requestId, Instant.now());
    }

    public record Error(String code, String message) {
    }
}
