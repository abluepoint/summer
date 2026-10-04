package com.abluepoint.summer.sample.operations;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('SCOPE_platform.capabilities.read')")
class RedisDiagnosticsController {

    private final RedisDiagnosticService service;
    private final RequestIdAccessor requestIdAccessor;

    RedisDiagnosticsController(RedisDiagnosticService service, RequestIdAccessor requestIdAccessor) {
        this.service = service;
        this.requestIdAccessor = requestIdAccessor;
    }

    @GetMapping("/internal/v1/diagnostics/redis")
    @RawResponse
    ResponseEntity<ApiResponse<RedisDiagnosticView>> redis() {
        String requestId = requestIdAccessor.currentRequestId();
        return ResponseEntity.ok()
                .header("X-Request-Id", requestId)
                .body(ApiResponse.success(service.check(), requestId));
    }
}
