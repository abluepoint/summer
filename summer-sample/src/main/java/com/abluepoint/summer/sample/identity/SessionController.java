package com.abluepoint.summer.sample.identity;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.mvc.api.RawResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class SessionController {

    private final SessionSnapshotService snapshotService;
    private final RequestIdAccessor requestIdAccessor;

    SessionController(SessionSnapshotService snapshotService, RequestIdAccessor requestIdAccessor) {
        this.snapshotService = snapshotService;
        this.requestIdAccessor = requestIdAccessor;
    }

    @GetMapping("/api/v1/session")
    @RawResponse
    ResponseEntity<ApiResponse<SessionSnapshot>> session(Authentication authentication, HttpServletRequest request) {
        SessionSnapshot snapshot = snapshotService.snapshot(authentication);
        String etag = quote(snapshot.revision());
        if (etag.equals(request.getHeader(HttpHeaders.IF_NONE_MATCH))) {
            return ResponseEntity.status(304)
                    .eTag(etag)
                    .header("X-Request-Id", requestIdAccessor.currentRequestId())
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .body(ApiResponse.success(snapshot, requestIdAccessor.currentRequestId()));
    }

    @DeleteMapping("/api/v1/session")
    ResponseEntity<Void> logout() {
        // Product-session destruction is owned by the BFF. The IAM endpoint is
        // intentionally idempotent so an expired/unknown session is harmless.
        return ResponseEntity.noContent().build();
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }
}
