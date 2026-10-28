package com.abluepoint.summer.sample.identity.api;

import com.abluepoint.summer.sample.identity.application.CapabilitySetView;
import com.abluepoint.summer.sample.identity.application.CapabilitySetService;

import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CapabilityController {

    private final CapabilitySetService capabilitySetService;
    private final RequestIdAccessor requestIdAccessor;

    CapabilityController(CapabilitySetService capabilitySetService, RequestIdAccessor requestIdAccessor) {
        this.capabilitySetService = capabilitySetService;
        this.requestIdAccessor = requestIdAccessor;
    }

    @GetMapping("/api/v1/platform/capabilities")
    @PreAuthorize("hasAuthority('SCOPE_capabilities.platform.read')")
    @RawResponse
    ResponseEntity<ApiResponse<CapabilitySetView>> platform(
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(capabilitySetService.platform(authentication), request);
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/capabilities")
    @PreAuthorize("hasAuthority('SCOPE_capabilities.enterprise.read')")
    @RawResponse
    ResponseEntity<ApiResponse<CapabilitySetView>> enterprise(
            @PathVariable String enterpriseId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return response(capabilitySetService.enterprise(enterpriseId, authentication), request);
    }

    private ResponseEntity<ApiResponse<CapabilitySetView>> response(
            CapabilitySetView capabilitySet,
            HttpServletRequest request
    ) {
        String etag = quote(capabilitySet.revision());
        if (etag.equals(request.getHeader(HttpHeaders.IF_NONE_MATCH))) {
            return ResponseEntity.status(304)
                    .eTag(etag)
                    .header("X-Request-Id", requestIdAccessor.currentRequestId())
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .header("X-Request-Id", requestIdAccessor.currentRequestId())
                .body(ApiResponse.success(capabilitySet, requestIdAccessor.currentRequestId()));
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }
}
