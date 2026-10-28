package com.abluepoint.summer.sample.resilience.controller;

import com.abluepoint.summer.sample.resilience.model.view.ResilienceDiagnosticsView;
import com.abluepoint.summer.sample.resilience.service.ResilientReadService;

import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import com.abluepoint.summer.sample.authorization.AuthorizationService;
import com.abluepoint.summer.sample.authorization.EnterpriseRequestContext;
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
    private final AuthorizationService authorization;

    ResilienceDiagnosticsController(ResilientReadService reads, RequestIdAccessor requestId,
                                    AuthorizationService authorization) {
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
            allowed = authorization.decision(authentication, EnterpriseRequestContext.resolve(authentication),
                    "platform.capabilities.read", id).allowed();
        } catch (RuntimeException unverifiedPermission) {
            throw new AccessDeniedException("Resilience diagnostics require verified permission");
        }
        if (!allowed) throw new AccessDeniedException("Resilience diagnostics permission denied");
        return ResponseEntity.ok().header("X-Request-Id", id)
                .body(ApiResponse.success(new ResilienceDiagnosticsView(id, Instant.now(), reads.snapshot()), id));
    }
}
