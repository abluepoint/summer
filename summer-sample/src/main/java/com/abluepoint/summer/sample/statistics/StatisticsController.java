package com.abluepoint.summer.sample.statistics;

import com.abluepoint.summer.common.response.ApiError;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
final class StatisticsController {
    private final StatisticsService service;
    private final RequestIdAccessor requestId;

    StatisticsController(StatisticsService service, RequestIdAccessor requestId) {
        this.service = service;
        this.requestId = requestId;
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/statistics")
    @RawResponse
    ResponseEntity<ApiResponse<StatisticsView>> read(
            @PathVariable String enterpriseId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String organizationId,
            Authentication authentication) {
        try {
            Instant fromTime = parse(from);
            Instant toTime = parse(to);
            return ok(service.read(enterpriseId, fromTime, toTime, organizationId, authentication));
        } catch (AccessDeniedException exception) {
            return error(HttpStatus.FORBIDDEN, "SUMMER_FORBIDDEN", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, "SUMMER_STATISTICS_INVALID", exception.getMessage());
        } catch (RuntimeException exception) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "SUMMER_INTERNAL_ERROR", "Statistics query failed");
        }
    }

    private Instant parse(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("from and to must be ISO-8601 instants"); }
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T value) {
        return ResponseEntity.ok().header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.success(value, requestId.currentRequestId()));
    }

    private <T> ResponseEntity<ApiResponse<T>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.failure(new ApiError(code, message == null ? code : message, List.of()), requestId.currentRequestId()));
    }
}
