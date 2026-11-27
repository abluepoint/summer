package com.abluepoint.summer.sample.enterprise;

import com.abluepoint.summer.mvc.annotation.Field;

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
    private final com.abluepoint.summer.sample.resource.ManagedConsumerAuthorization managedAccess;

    private final EnterpriseRegistry registry;
    private final RequestIdAccessor requestId;

    EnterpriseDirectoryController(EnterpriseRegistry registry, RequestIdAccessor requestId) { this(registry, requestId, null); }

    @org.springframework.beans.factory.annotation.Autowired
    EnterpriseDirectoryController(EnterpriseRegistry registry, RequestIdAccessor requestId, com.abluepoint.summer.sample.resource.ManagedConsumerAuthorization managedAccess) {
        this.managedAccess = managedAccess;
        this.registry = registry;
        this.requestId = requestId;
    }

    @Field(param = "name", required = false, nullable = true, style = "requestTextStyle")
    @Field(param = "code", required = false, nullable = true, style = "requestTextStyle")
    @PostMapping(value = "/api/v1/platform/enterprises", consumes = "application/json")

    @PreAuthorize("hasAuthority('SCOPE_platform.enterprise.write')")
    @RawResponse
    ResponseEntity<ApiResponse<EnterpriseRegistry.EnterpriseView>> create(@RequestBody CreateEnterpriseRequest request, org.springframework.security.core.Authentication authentication) {
        requireAccess(authentication, "platform.enterprise.write");
        try {
            return ResponseEntity.status(HttpStatus.CREATED).header("X-Request-Id", requestId.currentRequestId())
                    .body(ApiResponse.success(registry.create(request.name(), request.code(), authentication.getName(), requestId.currentRequestId()), requestId.currentRequestId()));
        } catch (IllegalArgumentException exception) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_ENTERPRISE_INVALID", exception.getMessage());
        } catch (IllegalStateException exception) {
            return failure(HttpStatus.CONFLICT, "SUMMER_ENTERPRISE_CONFLICT", exception.getMessage());
        }
    }

    @Field(param = "enterpriseId", style = "requestTextStyle")
    @GetMapping("/api/v1/platform/enterprises/{enterpriseId}/audit")

    @PreAuthorize("hasAuthority('SCOPE_platform.enterprise.read')")
    @RawResponse
    ResponseEntity<ApiResponse<List<PlatformEnterpriseDirectoryService.EnterpriseAudit>>> audit(
            @PathVariable String enterpriseId, org.springframework.security.core.Authentication authentication) {
        requireAccess(authentication, "platform.enterprise.read");
        return ResponseEntity.ok().header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.success(registry.audit(enterpriseId), requestId.currentRequestId()));
    }

    private <T> ResponseEntity<ApiResponse<T>> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.failure(new ApiError(code, message, List.of()), requestId.currentRequestId()));
    }

    record CreateEnterpriseRequest(String name, String code) {}
    private void requireAccess(org.springframework.security.core.Authentication actor, String scope) {
        if (managedAccess != null && managedAccess.require(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), actor, scope)) {
            if (actor instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt) {
                Object memberships = jwt.getToken().getClaims().get("enterprise_memberships");
                if (memberships instanceof java.util.Collection<?> values && !values.isEmpty()
                    && !"USER".equals(String.valueOf(jwt.getToken().getClaims().get("principal_type"))))
                    throw new org.springframework.security.access.AccessDeniedException("platform identity required");
            }
            return;
        }
        PlatformEnterpriseDirectoryService.require(actor, scope);
    }

}
