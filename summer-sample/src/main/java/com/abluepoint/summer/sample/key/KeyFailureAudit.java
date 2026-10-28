package com.abluepoint.summer.sample.key;

import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import com.abluepoint.summer.security.audit.spi.SecurityAuditLogger;

import java.util.Map;

/** Failure-only metadata; never accepts exceptions, credentials or key material. */
final class KeyFailureAudit {
    private final SecurityAuditLogger audit;
    private final SecurityAuditLogger fallback;
    private final RequestIdAccessor requestId;

    KeyFailureAudit(SecurityAuditLogger audit, RequestIdAccessor requestId, SecurityAuditLogger fallback) {
        this.audit = audit;
        this.fallback = fallback;
        this.requestId = requestId;
    }

    void readFailed(String keyId) {
        record("key_read_failure", "read", "KEY_SOURCE_UNAVAILABLE", keyId);
    }

    void signFailed() {
        record("key_sign_failure", "sign", "SIGNING_FAILED", null);
    }

    private void record(String event, String operation, String category, String keyId) {
        Map<String, String> attributes = Map.of(
                "operation", operation,
                "failureCategory", category,
                "keyId", safeKeyId(keyId),
                "requestId", currentRequestId());
        try {
            audit.record(event, "system", attributes);
        } catch (RuntimeException unavailable) {
            // A replaceable audit sink may use the same failing database as the key store.
            try {
                fallback.record(event, "system", attributes);
            } catch (RuntimeException loggingUnavailable) {
                // Preserve the original key failure even if both audit sinks are unavailable.
            }
        }
    }

    private String currentRequestId() {
        try {
            String value = requestId.currentRequestId();
            return value != null && value.matches("[A-Za-z0-9._-]{1,128}") ? value : "unbound";
        } catch (RuntimeException unavailable) {
            return "unbound";
        }
    }

    private static String safeKeyId(String value) {
        // MySQL keys use generated UUIDs; never log arbitrary stored or caller-provided text.
        return value != null && value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                ? value : "unavailable";
    }
}
