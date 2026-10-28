package com.abluepoint.summer.sample.identity;

import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import jakarta.servlet.http.HttpServletRequest;
import com.abluepoint.summer.security.context.SummerSecurityContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
final class SessionController {

    private final SessionSnapshotService snapshotService;
    private final RequestIdAccessor requestIdAccessor;
    private final SessionIndexService sessionIndex;

    SessionController(SessionSnapshotService snapshotService, RequestIdAccessor requestIdAccessor,
                      ObjectProvider<SessionIndexService> sessionIndex) {
        this.snapshotService = snapshotService;
        this.requestIdAccessor = requestIdAccessor;
        this.sessionIndex = sessionIndex.getIfAvailable();
    }

    @GetMapping("/api/v1/session")
    @RawResponse
    ResponseEntity<ApiResponse<SessionSnapshot>> session(Authentication authentication, HttpServletRequest request) {
        try {
            if (sessionIndex != null && !SummerSecurityContext.hasBearerCredentials(request))
                sessionIndex.assertActive(request, authentication);
            SessionSnapshot snapshot = snapshotService.snapshot(authentication);
            String etag = quote(snapshot.revision());
            if (etag.equals(request.getHeader(HttpHeaders.IF_NONE_MATCH))) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(etag)
                        .header("X-Request-Id", requestIdAccessor.currentRequestId())
                        .build();
            }
            return ResponseEntity.ok()
                    .eTag(etag)
                    .body(ApiResponse.success(snapshot, requestIdAccessor.currentRequestId()));
        } catch (SessionRevokedException exception) {
            return failure(HttpStatus.UNAUTHORIZED, "SUMMER_SESSION_REVOKED", exception.getMessage());
        } catch (SessionIndexUnavailableException exception) {
            return failure(HttpStatus.SERVICE_UNAVAILABLE, "SUMMER_SESSION_STORE_UNAVAILABLE", exception.getMessage());
        }
    }

    @GetMapping("/api/v1/sessions")
    @RawResponse
    ResponseEntity<ApiResponse<SessionIndexPage>> sessions(
            @RequestParam(value = "principal", required = false) String principal,
            Authentication authentication,
            HttpServletRequest request) {
        try {
            if (sessionIndex == null) return ok(new SessionIndexPage("UNAVAILABLE", List.of()));
            return ok(sessionIndex.list(principal, authentication, request));
        } catch (AccessDeniedException exception) {
            return failure(HttpStatus.FORBIDDEN, "SUMMER_FORBIDDEN", exception.getMessage());
        } catch (SessionIndexUnavailableException exception) {
            return failure(HttpStatus.SERVICE_UNAVAILABLE, "SUMMER_SESSION_STORE_UNAVAILABLE", exception.getMessage());
        }
    }

    @DeleteMapping("/api/v1/sessions/{reference}")
    @RawResponse
    ResponseEntity<ApiResponse<SessionIndexOperation>> revoke(
            @PathVariable String reference, Authentication authentication, HttpServletRequest request) {
        try {
            if (sessionIndex == null) {
                return failure(HttpStatus.SERVICE_UNAVAILABLE, "SUMMER_SESSION_STORE_UNAVAILABLE", "Shared session storage is unavailable");
            }
            return ok(sessionIndex.revoke(reference, authentication, request));
        } catch (SessionReferenceInvalidException exception) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_SESSION_REFERENCE_INVALID", exception.getMessage());
        } catch (SessionNotFoundException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_SESSION_NOT_FOUND", exception.getMessage());
        } catch (AccessDeniedException exception) {
            return failure(HttpStatus.FORBIDDEN, "SUMMER_FORBIDDEN", exception.getMessage());
        } catch (SessionIndexUnavailableException exception) {
            return failure(HttpStatus.SERVICE_UNAVAILABLE, "SUMMER_SESSION_STORE_UNAVAILABLE", exception.getMessage());
        }
    }

    @DeleteMapping("/api/v1/session")
    @RawResponse
    ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request) {
        // Product-session destruction is owned by the BFF. The IAM endpoint is
        // intentionally idempotent so an expired/unknown session is harmless.
        try {
            if (sessionIndex != null) sessionIndex.revokeCurrent(request, authentication);
            if (request.getSession(false) != null) request.getSession(false).invalidate();
            return ResponseEntity.noContent().build();
        } catch (SessionIndexUnavailableException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Request-Id", requestIdAccessor.currentRequestId()).build();
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T value) {
        return ResponseEntity.ok().header("X-Request-Id", requestIdAccessor.currentRequestId())
                .body(ApiResponse.success(value, requestIdAccessor.currentRequestId()));
    }

    private <T> ResponseEntity<ApiResponse<T>> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).header("X-Request-Id", requestIdAccessor.currentRequestId())
                .body(ApiResponse.failure(new ApiError(code, message, List.of()), requestIdAccessor.currentRequestId()));
    }

    private static String quote(String value) { return "\"" + value + "\""; }
}
