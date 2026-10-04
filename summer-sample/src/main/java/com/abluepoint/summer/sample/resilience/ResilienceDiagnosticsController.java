package com.abluepoint.summer.sample.resilience;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
class ResilienceDiagnosticsController {
    private final ResilientReadService reads;
    private final RequestIdAccessor requestId;

    ResilienceDiagnosticsController(ResilientReadService reads, RequestIdAccessor requestId) {
        this.reads = reads;
        this.requestId = requestId;
    }

    @GetMapping("/internal/v1/diagnostics/resilience")
    @PreAuthorize("hasAuthority('SCOPE_platform.capabilities.read')")
    @RawResponse
    ResponseEntity<ApiResponse<ResilienceDiagnosticsView>> snapshot() {
        String id = requestId.currentRequestId();
        return ResponseEntity.ok().header("X-Request-Id", id)
                .body(ApiResponse.success(new ResilienceDiagnosticsView(id, Instant.now(), reads.snapshot()), id));
    }
}
