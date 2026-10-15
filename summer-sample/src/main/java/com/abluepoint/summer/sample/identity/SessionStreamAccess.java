package com.abluepoint.summer.sample.identity;

import com.abluepoint.summer.security.autoconfigure.storage.BearerOrSessionSecurityContextRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.function.BooleanSupplier;

/** Captures a session reference, never the servlet request, for long-lived delivery. */
@Component
public final class SessionStreamAccess {
    private final SessionIndexService index;
    private final boolean shared;

    SessionStreamAccess(ObjectProvider<SessionIndexService> index,
                        com.abluepoint.summer.security.autoconfigure.SummerSecurityProperties properties) {
        this.index = index.getIfAvailable();
        this.shared = "redis".equalsIgnoreCase(properties.getStorage().getSession());
    }

    public BooleanSupplier capture(HttpServletRequest request, Authentication authentication) {
        // A bearer request remains independent of any login cookie sent alongside it.
        if (BearerOrSessionSecurityContextRepository.hasBearerCredentials(request)) return () -> true;
        var session = request.getSession(false);
        if (session == null || authentication == null) return () -> false;
        String sessionId = session.getId();
        String principal = authentication.getName();
        if (shared) {
            if (index == null) return () -> false;
            return () -> {
                SessionRecord record = index.load(sessionId);
                return record != null && "ACTIVE".equals(record.status())
                        && principal.equals(record.principal()) && record.expiresAt() != null
                        && record.expiresAt().isAfter(Instant.now());
            };
        }
        return () -> {
            try {
                Object value = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                return value instanceof SecurityContext context && context.getAuthentication() != null
                        && context.getAuthentication().isAuthenticated()
                        && principal.equals(context.getAuthentication().getName());
            } catch (IllegalStateException invalidated) {
                return false;
            }
        };
    }
}
