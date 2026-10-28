package com.abluepoint.summer.sample.operations.controller;

import com.abluepoint.summer.sample.operations.model.view.RuntimeDiagnosticsView;
import com.abluepoint.summer.sample.operations.service.RuntimeDiagnosticsService;

import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RuntimeDiagnosticsController {

    private final RuntimeDiagnosticsService service;
    private final RequestIdAccessor requestIdAccessor;

    RuntimeDiagnosticsController(RuntimeDiagnosticsService service, RequestIdAccessor requestIdAccessor) {
        this.service = service;
        this.requestIdAccessor = requestIdAccessor;
    }

    @GetMapping("/internal/v1/diagnostics/runtime")
    @PreAuthorize("hasAuthority('SCOPE_platform.capabilities.read')")
    @RawResponse
    ResponseEntity<ApiResponse<RuntimeDiagnosticsView>> runtime() {
        String requestId = requestIdAccessor.currentRequestId();
        return ResponseEntity.ok()
                .header("X-Request-Id", requestId)
                .body(ApiResponse.success(service.snapshot(), requestId));
    }
}
