package com.abluepoint.summer.sample.key;

import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import com.abluepoint.summer.security.audit.SecurityAuditLogger;
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
@ConditionalOnBean(ReferenceSummerKeyStore.class)
class ReferenceKeyCapabilityController {
    private final ReferenceSummerKeyStore store;
    private final RequestIdAccessor requestId;
    private final SecurityAuditLogger auditLogger;

    ReferenceKeyCapabilityController(ReferenceSummerKeyStore store, RequestIdAccessor requestId,
                                     SecurityAuditLogger auditLogger) {
        this.store = store;
        this.requestId = requestId;
        this.auditLogger = auditLogger;
    }

    @GetMapping
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_security.key.read')")
    ResponseEntity<ApiResponse<Map<String, Object>>> list(Authentication authentication) {
        List<Map<String, Object>> items = store.metadata();
        auditLogger.record("security_key_metadata_read", authentication.getName(),
                Map.of("requestId", requestId.currentRequestId(), "count", items.size()));
        return ResponseEntity.ok().header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.success(Map.of("items", items), requestId.currentRequestId()));
    }
}
