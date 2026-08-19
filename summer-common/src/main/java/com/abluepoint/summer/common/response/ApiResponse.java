package com.abluepoint.summer.common.response;

import java.time.Instant;

public record ApiResponse<T>(boolean success, T data, ApiError error, String requestId, Instant timestamp) {

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(true, data, null, requestId, Instant.now());
    }

    public static <T> ApiResponse<T> failure(ApiError error, String requestId) {
        return new ApiResponse<>(false, null, error, requestId, Instant.now());
    }
}
