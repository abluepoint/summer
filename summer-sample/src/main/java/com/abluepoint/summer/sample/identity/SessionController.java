package com.abluepoint.summer.sample.identity;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import jakarta.servlet.http.HttpServletRequest;
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
        if (sessionIndex != null && !com.abluepoint.summer.security.autoconfigure.storage.BearerOrSessionSecurityContextRepository.hasBearerCredentials(request))
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
            throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    @DeleteMapping("/api/v1/sessions/{reference}")
    @RawResponse
    ResponseEntity<ApiResponse<SessionIndexOperation>> revoke(
            @PathVariable String reference, Authentication authentication, HttpServletRequest request) {
        try {
            if (sessionIndex == null) {
                throw SummerException.withMessage(SampleTransportErrorCode.SESSION_STORE_UNAVAILABLE, "Shared session storage is unavailable");
            }
            return ok(sessionIndex.revoke(reference, authentication, request));
        } catch (AccessDeniedException exception) {
            throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, exception.getMessage(), exception);
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
        } catch (SummerException exception) {
            if (exception.errorCode() != SampleTransportErrorCode.SESSION_STORE_UNAVAILABLE) throw exception;
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Request-Id", requestIdAccessor.currentRequestId()).build();
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T value) {
        return ResponseEntity.ok().header("X-Request-Id", requestIdAccessor.currentRequestId())
                .body(ApiResponse.success(value, requestIdAccessor.currentRequestId()));
    }

    private static String quote(String value) { return "\"" + value + "\""; }
}
