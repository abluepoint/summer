package com.abluepoint.summer.sample.enterprise;

import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
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
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.enterprise.write")
    @PreAuthorize("hasAuthority('SCOPE_platform.enterprise.write')")
    @RawResponse
    ResponseEntity<ApiResponse<EnterpriseRegistry.EnterpriseView>> create(@RequestBody CreateEnterpriseRequest request, org.springframework.security.core.Authentication authentication) {
        PlatformEnterpriseDirectoryService.require(authentication, "platform.enterprise.write");
        try {
            return ResponseEntity.status(HttpStatus.CREATED).header("X-Request-Id", requestId.currentRequestId())
                    .body(ApiResponse.success(registry.create(request.name(), request.code(), authentication.getName(), requestId.currentRequestId()), requestId.currentRequestId()));
        } catch (IllegalArgumentException exception) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_ENTERPRISE_INVALID", exception.getMessage());
        } catch (IllegalStateException exception) {
            return failure(HttpStatus.CONFLICT, "SUMMER_ENTERPRISE_CONFLICT", exception.getMessage());
        }
    }

    @GetMapping("/api/v1/platform/enterprises/{enterpriseId}/audit")
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.enterprise.read")
    @PreAuthorize("hasAuthority('SCOPE_platform.enterprise.read')")
    @RawResponse
    ResponseEntity<ApiResponse<List<PlatformEnterpriseDirectoryService.EnterpriseAudit>>> audit(
            @PathVariable String enterpriseId, org.springframework.security.core.Authentication authentication) {
        PlatformEnterpriseDirectoryService.require(authentication, "platform.enterprise.read");
        return ResponseEntity.ok().header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.success(registry.audit(enterpriseId), requestId.currentRequestId()));
    }

    private <T> ResponseEntity<ApiResponse<T>> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.failure(new ApiError(code, message, List.of()), requestId.currentRequestId()));
    }

    record CreateEnterpriseRequest(String name, String code) {}
}
