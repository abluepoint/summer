package com.abluepoint.summer.sample.identity;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import com.abluepoint.summer.security.autoconfigure.storage.SummerSessionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SessionIndexProperties.class)
@org.springframework.context.annotation.Import({SharedHttpSessionConfiguration.class, SessionFailureConfiguration.class})
@ConditionalOnProperty(prefix = "summer.sample.session-index", name = "enabled", havingValue = "true")
class SessionIndexConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "session", havingValue = "redis")
    SummerSessionStore redisSummerSessionStore(SessionIndexService index) {
        return new RedisSummerSessionStore(index);
    }
}

/**
 * Redis is authoritative for authentication. The servlet session identifier is
 * supplied by the servlet/Spring Session layer; local contexts never override
 * missing, expired, revoked or unavailable shared records.
 */
final class RedisSummerSessionStore extends org.springframework.security.web.context.HttpSessionSecurityContextRepository
        implements SummerSessionStore {
    private final SessionIndexService index;

    RedisSummerSessionStore(SessionIndexService index) { this.index = index; }

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
        } catch (SummerException failure) {
            if (failure.errorCode() == SampleTransportErrorCode.SESSION_STORE_UNAVAILABLE) return false;
            throw failure;
        }
    }

    private static boolean active(SessionRecord record) {
        return record != null && "ACTIVE".equals(record.status()) && record.expiresAt() != null
                && record.expiresAt().isAfter(java.time.Instant.now());
    }

}
