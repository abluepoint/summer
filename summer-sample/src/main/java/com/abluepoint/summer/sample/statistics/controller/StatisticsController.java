package com.abluepoint.summer.sample.statistics.controller;

import com.abluepoint.summer.sample.statistics.model.view.StatisticsView;
import com.abluepoint.summer.sample.statistics.service.StatisticsQueryService;

import com.abluepoint.summer.common.error.api.CommonErrorCode;
import com.abluepoint.summer.common.error.api.ErrorCode;
import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.error.spi.ErrorStatusResolver;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
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
    private final StatisticsQueryService service;
    private final RequestIdAccessor requestId;

    private final ErrorStatusResolver errorStatus;

    StatisticsController(StatisticsQueryService service, RequestIdAccessor requestId, ErrorStatusResolver errorStatus) {
        this.service = service;
        this.requestId = requestId;
        this.errorStatus = errorStatus;
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/statistics")
    @RawResponse
    @com.abluepoint.summer.mvc.parameter.api.FieldContract
    @com.abluepoint.summer.mvc.parameter.api.Field(param="enterpriseId", style="statisticsIdentifierStyle")
    @com.abluepoint.summer.mvc.parameter.api.Field(param="organizationId", required=false, style="statisticsIdentifierStyle")
    @com.abluepoint.summer.mvc.parameter.api.Field(param="from", required=false, style="statisticsInstantStyle")
    @com.abluepoint.summer.mvc.parameter.api.Field(param="to", required=false, style="statisticsInstantStyle")
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
            return error(CommonErrorCode.INVALID_ARGUMENT, exception.getMessage());
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

    private <T> ResponseEntity<ApiResponse<T>> error(ErrorCode code, String message) {
        return error(errorStatus.resolve(code), code.code(), message);
    }

    private <T> ResponseEntity<ApiResponse<T>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.failure(new ApiError(code, message == null ? code : message, List.of()), requestId.currentRequestId()));
    }
}
