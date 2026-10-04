package com.abluepoint.summer.sample.enterprise;

import com.abluepoint.summer.common.response.ApiError;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
class EnterpriseDirectoryController {
    private final EnterpriseRegistry registry;
    private final RequestIdAccessor requestId;

    EnterpriseDirectoryController(EnterpriseRegistry registry, RequestIdAccessor requestId) {
        this.registry = registry;
        this.requestId = requestId;
    }

    @PostMapping(value = "/api/v1/platform/enterprises", consumes = "application/json")
    @PreAuthorize("hasAuthority('SCOPE_platform.enterprise.write')")
    @RawResponse
    ResponseEntity<ApiResponse<EnterpriseRegistry.EnterpriseView>> create(@RequestBody CreateEnterpriseRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).header("X-Request-Id", requestId.currentRequestId())
                    .body(ApiResponse.success(registry.create(request.name(), request.code()), requestId.currentRequestId()));
        } catch (IllegalArgumentException exception) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_ENTERPRISE_INVALID", exception.getMessage());
        } catch (IllegalStateException exception) {
            return failure(HttpStatus.CONFLICT, "SUMMER_ENTERPRISE_CONFLICT", exception.getMessage());
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.failure(new ApiError(code, message, List.of()), requestId.currentRequestId()));
    }

    record CreateEnterpriseRequest(String name, String code) {}
}
