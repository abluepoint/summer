package com.abluepoint.summer.security.audit.spi;

import java.util.Map;

@FunctionalInterface
public interface SecurityAuditLogger {

    void record(String event, String principal, Map<String, ?> attributes);
}
