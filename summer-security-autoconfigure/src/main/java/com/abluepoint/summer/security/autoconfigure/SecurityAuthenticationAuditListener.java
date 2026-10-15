package com.abluepoint.summer.security.autoconfigure;

import com.abluepoint.summer.security.audit.SecurityAuditLogger;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

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
        auditLogger.record(eventName, safePrincipal(event.getAuthentication()), Map.of(
                "authenticationType", event.getAuthentication().getClass().getSimpleName()
        ));
    }

    private static String safePrincipal(Authentication authentication) {
        String name = null;
        if (authentication instanceof UsernamePasswordAuthenticationToken) {
            if (authentication.getPrincipal() instanceof UserDetails user) name = user.getUsername();
            else if (authentication.getPrincipal() instanceof String username) name = username;
        } else if (authentication.isAuthenticated()) {
            if (authentication instanceof JwtAuthenticationToken jwt) name = jwt.getName();
            else if (authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) name = principal.getName();
        }
        // Unverified Bearer credentials and unknown principal objects are never identifiers.
        if (name == null || name.isBlank()) return "anonymous";
        return name.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", "_");
    }
}
