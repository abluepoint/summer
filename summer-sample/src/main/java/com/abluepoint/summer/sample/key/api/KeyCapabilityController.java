package com.abluepoint.summer.sample.key.api;

import com.abluepoint.summer.sample.key.application.KeyManagementService;
import com.abluepoint.summer.sample.key.application.KeyRotateCommand;

import com.abluepoint.summer.sample.key.application.AutomaticKeyRotationService;
import com.abluepoint.summer.sample.key.domain.KeyRotation;
import com.abluepoint.summer.sample.key.domain.KeyRotationPreconditionException;
import com.abluepoint.summer.sample.key.domain.KeyRotationValidationException;

import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import com.abluepoint.summer.security.audit.spi.SecurityAuditLogger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(prefix = "summer.security.storage", name = "keys", havingValue = "mysql")
public class KeyCapabilityController {
    private final KeyManagementService management;
    private final AutomaticKeyRotationService automaticRotation;
    private final RequestIdAccessor requestId;
    private final SecurityAuditLogger auditLogger;

    KeyCapabilityController(KeyManagementService management, AutomaticKeyRotationService automaticRotation,
                            RequestIdAccessor requestId, SecurityAuditLogger auditLogger) {
        this.management = management; this.automaticRotation = automaticRotation; this.requestId = requestId; this.auditLogger = auditLogger;
    }

    @GetMapping("/api/security/keys")
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_security.key.read')")
    ResponseEntity<ApiResponse<Map<String, Object>>> list(Authentication authentication) {
        List<Map<String, Object>> items = management.metadata(authentication.getName(), requestId.currentRequestId());
        return ResponseEntity.ok().eTag("\"" + management.currentVersion() + "\"")
                .header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.success(Map.of("items", items, "rotation", automaticRotation.status()), requestId.currentRequestId()));
    }

    @PostMapping("/api/security/keys:rotate")
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_security.key.rotate')")
    ResponseEntity<ApiResponse<Map<String, Object>>> rotate(@RequestBody KeyRotateRequest body,
                                                              @RequestHeader("If-Match") String ifMatch,
                                                              @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                              Authentication authentication) {
        try {
            int expected = Integer.parseInt(ifMatch.replace("\"", ""));
            KeyRotation result = management.rotate(new KeyRotateCommand(body == null ? 0 : body.retentionDays(), expected, idempotencyKey), authentication.getName(), requestId.currentRequestId());
            return ResponseEntity.ok().eTag("\"" + result.version() + "\"")
                    .header("X-Request-Id", requestId.currentRequestId())
                    .body(ApiResponse.success(Map.of("activeKid", result.activeKid(), "version", result.version(),
                            "retentionDays", result.retentionDays()), requestId.currentRequestId()));
        } catch (KeyRotationValidationException e) {
            auditFailure(authentication, "SUMMER_KEY_ROTATION_INVALID");
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_KEY_ROTATION_INVALID", e.getMessage());
        } catch (KeyRotationPreconditionException e) {
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

}
