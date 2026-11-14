package com.abluepoint.summer.sample.resilience;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.resource.ManagedConsumerAuthorization;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
class ResilienceDiagnosticsController {
    private final ResilientReadService reads;
    private final RequestIdAccessor requestId;
    private final ManagedConsumerAuthorization authorization;

    ResilienceDiagnosticsController(ResilientReadService reads, RequestIdAccessor requestId,
                                    ManagedConsumerAuthorization authorization) {
        this.reads = reads;
        this.requestId = requestId;
        this.authorization = authorization;
    }

    @GetMapping("/internal/v1/diagnostics/resilience")
    @PreAuthorize("hasAuthority('SCOPE_platform.capabilities.read')")
    @RawResponse
    ResponseEntity<ApiResponse<ResilienceDiagnosticsView>> snapshot(Authentication authentication) {
        String id = requestId.currentRequestId();
        boolean allowed;
        try {
            allowed = authorization.require(Domain.platform(), authentication, "platform.capabilities.read");
        } catch (RuntimeException unverifiedPermission) {
            throw new AccessDeniedException("Resilience diagnostics require verified permission", unverifiedPermission);
        }
        if (!allowed) throw new AccessDeniedException("Resilience diagnostics permission denied");
        return ResponseEntity.ok().header("X-Request-Id", id)
                .body(ApiResponse.success(new ResilienceDiagnosticsView(id, Instant.now(), reads.snapshot()), id));
    }
}
