package com.abluepoint.summer.sample.key.application;

import com.abluepoint.summer.sample.key.domain.KeyManagementStore;
import com.abluepoint.summer.sample.key.domain.KeyRotation;
import com.abluepoint.summer.security.audit.spi.SecurityAuditLogger;
import java.util.List;
import java.util.Map;

/** Coordinates administrator operations and their successful security audit. */
public class KeyManagementService {
    private final KeyManagementStore store;
    private final SecurityAuditLogger audit;
    public KeyManagementService(KeyManagementStore store, SecurityAuditLogger audit) {
        this.store = store; this.audit = audit;
    }
    public List<Map<String, Object>> metadata(String actor, String requestId) {
        var items = store.metadata();
        audit.record("security_key_metadata_read", actor, Map.of("requestId", requestId, "count", items.size()));
        return items;
    }
    public int currentVersion() { return store.currentVersion(); }
    public KeyRotation rotate(KeyRotateCommand command, String actor, String requestId) {
        var result = store.rotate(command.retentionDays(), command.expectedVersion(), command.idempotencyKey());
        audit.record("security_key_rotated", actor, Map.of("requestId", requestId,
                "activeKid", result.activeKid(), "version", result.version(), "retentionDays", result.retentionDays()));
        return result;
    }
}
