package com.abluepoint.summer.sample.enterprise;

import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("platform.enterprise.read")
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
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("platform.enterprise.write")
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
        } catch (PlatformEnterprisePreconditionException e) {
            return error(HttpStatus.PRECONDITION_FAILED, "SUMMER_PRECONDITION_FAILED", "The enterprise has changed");
        } catch (PlatformEnterpriseConflictException e) {
            return error(HttpStatus.CONFLICT, "SUMMER_IDEMPOTENCY_CONFLICT", e.getMessage());
        } catch (PlatformEnterpriseValidationException e) {
            return error(HttpStatus.BAD_REQUEST, "SUMMER_INVALID_ENTERPRISE_ACTION", e.getMessage());
        } catch (PlatformEnterpriseNotFoundException e) {
            return error(HttpStatus.NOT_FOUND, "SUMMER_ENTERPRISE_NOT_FOUND", e.getMessage());
        } catch (AccessDeniedException e) {
            return error(HttpStatus.FORBIDDEN, "SUMMER_FORBIDDEN", e.getMessage());
        }
    }

    private ResponseEntity<ApiResponse<PlatformEnterpriseView>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.failure(new ApiError(code, message, List.of()), requestId.currentRequestId()));
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }
}
