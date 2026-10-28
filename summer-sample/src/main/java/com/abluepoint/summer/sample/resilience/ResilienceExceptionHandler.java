package com.abluepoint.summer.sample.resilience;

import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
@Order(-10)
class ResilienceExceptionHandler {
    private final RequestIdAccessor requestId;

    ResilienceExceptionHandler(RequestIdAccessor requestId) { this.requestId = requestId; }

    @ExceptionHandler(DependencyDegradedException.class)
    ResponseEntity<ApiResponse<Void>> unavailable(DependencyDegradedException ignored) {
        String id = requestId.currentRequestId();
        return ResponseEntity.status(503).header("X-Request-Id", id)
                .body(ApiResponse.failure(new ApiError("SUMMER_DEPENDENCY_UNAVAILABLE",
                        "The dependency is unavailable and no safe fallback is available", List.of()), id));
    }
}
