package com.abluepoint.summer.security.audit;

import java.util.Map;

@FunctionalInterface
public interface SecurityAuditLogger {

    void record(String event, String principal, Map<String, ?> attributes);
}
