package com.abluepoint.summer.sample.key;

import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.security.audit.SecurityAuditLogger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

@RestController
@ConditionalOnBean(MysqlSummerKeyStore.class)
class KeyCapabilityController {
    private final MysqlSummerKeyStore store;
    private final AutomaticKeyRotationService automaticRotation;
    private final RequestIdAccessor requestId;
    private final SecurityAuditLogger auditLogger;

    KeyCapabilityController(MysqlSummerKeyStore store, AutomaticKeyRotationService automaticRotation,
                            RequestIdAccessor requestId, SecurityAuditLogger auditLogger) {
        this.store = store; this.automaticRotation = automaticRotation; this.requestId = requestId; this.auditLogger = auditLogger;
    }

    @GetMapping("/api/security/keys")
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_security.key.read')")
    ResponseEntity<ApiResponse<Map<String, Object>>> list(Authentication authentication) {
        List<Map<String, Object>> items = store.metadata();
        auditLogger.record("security_key_metadata_read", authentication.getName(),
                Map.of("requestId", requestId.currentRequestId(), "count", items.size()));
        return ResponseEntity.ok().eTag("\"" + store.currentVersion() + "\"")
                .header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.success(Map.of("items", items, "rotation", automaticRotation.status()), requestId.currentRequestId()));
    }

    @PostMapping("/api/security/keys:rotate")
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_security.key.rotate')")
    ResponseEntity<ApiResponse<Map<String, Object>>> rotate(@RequestBody RotationRequest body,
                                                              @RequestHeader("If-Match") String ifMatch,
                                                              @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                              Authentication authentication) {
        try {
            int expected = Integer.parseInt(ifMatch.replace("\"", ""));
            MysqlSummerKeyStore.Rotation result = store.rotate(body == null ? 0 : body.retentionDays(), expected, idempotencyKey);
            auditLogger.record("security_key_rotated", authentication.getName(),
                    Map.of("requestId", requestId.currentRequestId(), "activeKid", result.activeKid(),
                            "version", result.version(), "retentionDays", result.retentionDays()));
            return ResponseEntity.ok().eTag("\"" + result.version() + "\"")
                    .header("X-Request-Id", requestId.currentRequestId())
                    .body(ApiResponse.success(Map.of("activeKid", result.activeKid(), "version", result.version(),
                            "retentionDays", result.retentionDays()), requestId.currentRequestId()));
        } catch (MysqlSummerKeyStore.KeyRotationValidationException e) {
            auditFailure(authentication, "SUMMER_KEY_ROTATION_INVALID");
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_KEY_ROTATION_INVALID", e.getMessage());
        } catch (MysqlSummerKeyStore.KeyRotationPreconditionException e) {
            auditFailure(authentication, "SUMMER_PRECONDITION_FAILED");
            return failure(HttpStatus.PRECONDITION_FAILED, "SUMMER_PRECONDITION_FAILED", e.getMessage());
        } catch (NumberFormatException e) {
            auditFailure(authentication, "SUMMER_KEY_ROTATION_INVALID");
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_KEY_ROTATION_INVALID", "If-Match must contain a key version");
        } catch (Exception e) {
            auditLogger.record("security_key_rotation_failed", authentication.getName(),
                    Map.of("requestId", requestId.currentRequestId(), "result", "FAILED"));
            return failure(HttpStatus.INTERNAL_SERVER_ERROR, "SUMMER_INTERNAL_ERROR", "Key rotation failed");
        }
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.failure(new com.abluepoint.summer.common.response.api.ApiError(code, message, List.of()), requestId.currentRequestId()));
    }

    private void auditFailure(Authentication authentication, String code) {
        auditLogger.record("security_key_rotation_failed", authentication.getName(),
                Map.of("requestId", requestId.currentRequestId(), "result", code));
    }

    record RotationRequest(int retentionDays) {}
}
