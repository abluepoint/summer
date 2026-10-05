package com.abluepoint.summer.sample.identity;

import com.abluepoint.summer.security.autoconfigure.storage.SummerSessionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SessionIndexProperties.class)
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
        try {
            SessionRecord shared = index.load(session.getId());
            if (!active(shared)) {
                return org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
            }
            return context(shared);
        } catch (SessionIndexUnavailableException ignored) {
            return org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        }
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
        return record != null && "ACTIVE".equals(record.status()) && record.expiresAt() != null
                && record.expiresAt().isAfter(java.time.Instant.now());
    }

    private static org.springframework.security.core.context.SecurityContext context(SessionRecord record) {
        org.springframework.security.core.context.SecurityContext context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        java.util.List<org.springframework.security.core.GrantedAuthority> authorities = record.authorities().stream()
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).map(value -> (org.springframework.security.core.GrantedAuthority) value).toList();
        if (record.claims().isEmpty()) {
            context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(record.principal(), null, authorities));
            return context;
        }
        org.springframework.security.oauth2.jwt.Jwt.Builder jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("shared-session-context")
                .header("alg", "none").subject(record.principal()).claims(values -> values.putAll(record.claims()));
        if (record.createdAt() != null) jwt.issuedAt(record.createdAt());
        if (record.expiresAt() != null) jwt.expiresAt(record.expiresAt());
        context.setAuthentication(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt.build(), authorities, record.principal()));
        return context;
    }
}
