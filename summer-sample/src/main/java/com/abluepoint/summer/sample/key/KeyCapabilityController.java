package com.abluepoint.summer.sample.key;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.security.audit.SecurityAuditLogger;
import com.nimbusds.jose.jwk.JWK;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/security/keys")
@ConditionalOnBean(MysqlSummerKeyStore.class)
class KeyCapabilityController {
    private final MysqlSummerKeyStore store;
    private final RequestIdAccessor requestId;
    private final SecurityAuditLogger auditLogger;

    KeyCapabilityController(MysqlSummerKeyStore store, RequestIdAccessor requestId, SecurityAuditLogger auditLogger) {
        this.store = store; this.requestId = requestId; this.auditLogger = auditLogger;
    }

    @GetMapping
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_security.key.read')")
    ResponseEntity<ApiResponse<Map<String, Object>>> list(Authentication authentication) {
        List<Map<String, Object>> items = store.current().getKeys().stream().map(this::metadata).toList();
        auditLogger.record("security_key_metadata_read", authentication.getName(),
                Map.of("requestId", requestId.currentRequestId(), "count", items.size()));
        return ResponseEntity.ok().header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.success(Map.of("items", items), requestId.currentRequestId()));
    }

    private Map<String, Object> metadata(JWK key) {
        return Map.of("kid", key.getKeyID(), "algorithm", "RS256", "source", "mysql", "status", "ACTIVE", "version", 1, "health", "READY");
    }
}
