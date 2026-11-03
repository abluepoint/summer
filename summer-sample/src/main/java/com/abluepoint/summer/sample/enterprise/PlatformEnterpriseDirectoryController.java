package com.abluepoint.summer.sample.enterprise;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PlatformEnterpriseDirectoryController {
    private final PlatformEnterpriseDirectoryService service;
    private final RequestIdAccessor requestId;

    PlatformEnterpriseDirectoryController(PlatformEnterpriseDirectoryService service, RequestIdAccessor requestId) {
        this.service = service;
        this.requestId = requestId;
    }

    @GetMapping("/api/v1/platform/enterprises")
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.enterprise.read")
    @PreAuthorize("hasAuthority('SCOPE_platform.enterprise.read')")
    @RawResponse
    ResponseEntity<ApiResponse<PlatformEnterprisePage>> list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String qualification,
            Authentication auth) {
        PlatformEnterprisePage body = service.list(search, status, qualification, auth);
        return ResponseEntity.ok().eTag(quote(java.util.UUID.nameUUIDFromBytes(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString()))
                .header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.success(body, requestId.currentRequestId()));
    }

    @PostMapping({
            "/api/v1/platform/enterprises/{id}:freeze",
            "/api/v1/platform/enterprises/{id}:disable",
            "/api/v1/platform/enterprises/{id}:reject"
    })
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.enterprise.write")
    @PreAuthorize("hasAuthority('SCOPE_platform.enterprise.write')")
    @RawResponse
    ResponseEntity<ApiResponse<PlatformEnterpriseView>> transition(
            @PathVariable String id,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestHeader("Idempotency-Key") String key,
            Authentication auth,
            jakarta.servlet.http.HttpServletRequest request) {
        String action = request.getRequestURI().substring(request.getRequestURI().lastIndexOf(':') + 1);
        try {
            PlatformEnterpriseView body = service.transition(id, action, ifMatch, key, requestId.currentRequestId(), auth);
            return ResponseEntity.ok().eTag(quote(body.revision())).header("X-Request-Id", requestId.currentRequestId()).body(ApiResponse.success(body, requestId.currentRequestId()));
        } catch (AccessDeniedException e) {
            throw SummerException.withMessage(
                    CommonErrorCode.FORBIDDEN, e.getMessage(), e);
        }
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }
}
