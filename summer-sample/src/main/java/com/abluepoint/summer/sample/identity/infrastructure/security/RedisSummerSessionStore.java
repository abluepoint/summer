package com.abluepoint.summer.sample.identity.infrastructure.security;

import com.abluepoint.summer.sample.identity.application.SessionIndexService;
import com.abluepoint.summer.sample.identity.domain.SessionIndexUnavailableException;
import com.abluepoint.summer.sample.identity.domain.SessionRecord;

import com.abluepoint.summer.security.session.spi.SummerSessionStore;

public final class RedisSummerSessionStore extends org.springframework.security.web.context.HttpSessionSecurityContextRepository
        implements SummerSessionStore {
    private final SessionIndexService index;

    public RedisSummerSessionStore(SessionIndexService index) { this.index = index; }

    @Override
    public org.springframework.security.core.context.SecurityContext loadContext(
            org.springframework.security.web.context.HttpRequestResponseHolder holder) {
        jakarta.servlet.http.HttpServletRequest request = holder.getRequest();
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null) return org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        // Spring Session restores the complete authentication (including factor
        // timestamps and original token claims). The index may deny it, never mint it.
        org.springframework.security.core.context.SecurityContext stored = super.loadDeferredContext(request).get();
        if (stored.getAuthentication() == null) return stored;
        SessionRecord shared = index.touch(session.getId(), session.getMaxInactiveInterval());
        if (!active(shared)) return org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        return stored;
    }

    @Override
    public org.springframework.security.core.context.DeferredSecurityContext loadDeferredContext(jakarta.servlet.http.HttpServletRequest request) {
        return new org.springframework.security.core.context.DeferredSecurityContext() {
            private org.springframework.security.core.context.SecurityContext value;
            @Override public org.springframework.security.core.context.SecurityContext get() {
                if (value == null) value = loadContext(new org.springframework.security.web.context.HttpRequestResponseHolder(request, null));
                return value;
            }
            @Override public boolean isGenerated() { return get().getAuthentication() == null; }
        };
    }

    @Override
    public void saveContext(org.springframework.security.core.context.SecurityContext context,
                            jakarta.servlet.http.HttpServletRequest request,
                            jakarta.servlet.http.HttpServletResponse response) {
        org.springframework.security.core.Authentication authentication = context.getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
        jakarta.servlet.http.HttpSession session = request.getSession(authenticated);
        if (session == null) return;
        try {
            if (authenticated) index.ensureCurrent(request, authentication);
            else index.revokeCurrent(request, authentication);
            super.saveContext(context, request, response);
        } catch (RuntimeException failure) {
            session.removeAttribute(SPRING_SECURITY_CONTEXT_KEY);
            throw failure;
        }
    }

    @Override
    public boolean containsContext(jakarta.servlet.http.HttpServletRequest request) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session == null) return false;
        try {
            return active(index.load(session.getId()));
        } catch (SessionIndexUnavailableException ignored) {
            return false;
        }
    }

    private static boolean active(SessionRecord record) {
        return record != null && record.activeAt(java.time.Instant.now());
    }

}
