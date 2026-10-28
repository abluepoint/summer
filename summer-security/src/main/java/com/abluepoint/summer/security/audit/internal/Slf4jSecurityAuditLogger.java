package com.abluepoint.summer.security.audit.internal;

import com.abluepoint.summer.security.audit.spi.SecurityAuditLogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class Slf4jSecurityAuditLogger implements SecurityAuditLogger {

    private static final Logger audit = LoggerFactory.getLogger("com.abluepoint.summer.security.audit");

    @Override
    public void record(String event, String principal, Map<String, ?> attributes) {
        audit.info("security_event={} principal={} attributes={}", event, sanitize(principal), attributes);
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "anonymous";
        }
        return value.replaceAll("[\\r\\n\\t]", "_");
    }
}
