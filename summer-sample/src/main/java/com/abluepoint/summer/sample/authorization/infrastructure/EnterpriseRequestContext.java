package com.abluepoint.summer.sample.authorization.infrastructure;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.*;

/** Selects an explicit request tenant or an unambiguous authenticated membership. Never supplies a default. */
@org.springframework.modulith.NamedInterface("context")
public final class EnterpriseRequestContext {
    private EnterpriseRequestContext() {}
    public static String resolve(Authentication authentication) {
        Set<String> selected = new LinkedHashSet<>();
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            var request = attributes.getRequest();
            String path = request.getRequestURI().substring(request.getContextPath().length());
            for (String prefix : List.of("/api/v1/enterprises/", "/public/enterprises/")) {
                if (path.startsWith(prefix)) add(selected, path.substring(prefix.length()).split("/", 2)[0]);
            }
            for (String header : Collections.list(request.getHeaders("X-Enterprise-Id"))) add(selected, header);
            String[] query = request.getParameterValues("tenantId");
            if (query != null) for (String tenant : query) add(selected, tenant);
        }
        if (selected.size() > 1) throw denied();
        if (selected.isEmpty() && authentication instanceof JwtAuthenticationToken jwt) {
            add(selected, jwt.getToken().getClaimAsString("active_enterprise_id"));
            if (selected.isEmpty()) selected.addAll(activeMemberships(authentication));
        }
        if (selected.size() != 1) throw denied();
        String tenant = selected.iterator().next();
        if (authentication != null && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            requireMembership(tenant, authentication);
        }
        return tenant;
    }
    public static void requireMembership(String tenant, Authentication auth) {
        if (tenant == null || !tenant.matches("[A-Za-z0-9_-]{1,80}") || !activeMemberships(auth).contains(tenant)) throw denied();
    }
    public static Set<String> activeMemberships(Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwt)) return Set.of();
        Object claim = jwt.getToken().getClaims().get("enterprise_memberships");
        if (!(claim instanceof Collection<?> entries)) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (Object entry : entries) if (entry instanceof Map<?,?> map) {
            Object id = map.containsKey("enterpriseId") ? map.get("enterpriseId") : map.get("enterprise_id");
            Object status = map.containsKey("status") ? map.get("status") : map.get("membership_status");
            if (id instanceof String tenant && "ACTIVE".equalsIgnoreCase(String.valueOf(status))) result.add(tenant);
        }
        return Set.copyOf(result);
    }
    private static void add(Set<String> result, String value) {
        if (value == null) return;
        if (!value.matches("[A-Za-z0-9_-]{1,80}")) throw denied();
        result.add(value);
    }
    private static AccessDeniedException denied() { return new AccessDeniedException("An unambiguous active enterprise context is required"); }
    /** The legacy authorization-event API has an explicit default independent of tenant resolution. */
    public static String requestedEnterpriseId(String[] values) {
        if (values == null || values.length == 1 && values[0].isEmpty()) return "ent-acme";
        if (values.length != 1 || !values[0].matches("[A-Za-z0-9_-]{1,80}")) {
            throw new AccessDeniedException("A single valid enterpriseId is required");
        }
        return values[0];
    }
}
