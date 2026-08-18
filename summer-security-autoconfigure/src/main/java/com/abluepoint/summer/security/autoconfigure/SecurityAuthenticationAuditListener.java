package com.abluepoint.summer.security.autoconfigure;

import com.abluepoint.summer.security.audit.SecurityAuditLogger;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;

import java.util.Map;

final class SecurityAuthenticationAuditListener implements ApplicationListener<AbstractAuthenticationEvent> {

    private final SecurityAuditLogger auditLogger;

    SecurityAuthenticationAuditListener(SecurityAuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @Override
    public void onApplicationEvent(AbstractAuthenticationEvent event) {
        String eventName = event instanceof AbstractAuthenticationFailureEvent
                ? "authentication_failure"
                : "authentication_success";
        auditLogger.record(eventName, event.getAuthentication().getName(), Map.of(
                "authenticationType", event.getAuthentication().getClass().getSimpleName()
        ));
    }
}
