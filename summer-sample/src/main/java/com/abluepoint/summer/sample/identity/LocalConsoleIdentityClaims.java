package com.abluepoint.summer.sample.identity;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues current identity/context hints; resource grants stay in the authority. */
@Service
class LocalConsoleIdentityClaims {
    private final JdbcTemplate jdbc;
    private final String bootstrapUsername;
    LocalConsoleIdentityClaims(JdbcTemplate jdbc, @org.springframework.beans.factory.annotation.Value("${summer.security.authorization-server.user.username:}") String bootstrapUsername) {
        this.jdbc = jdbc; this.bootstrapUsername = bootstrapUsername;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resolve(String username, Map<String, Object> legacyClaims) {
        var users = jdbc.queryForList("select active from summer_auth_user where username = ?", Boolean.class, username);
        if (users.size() != 1 || !Boolean.TRUE.equals(users.getFirst())) throw new AccessDeniedException("Local user is unavailable");
        Map<String,Object> claims=new LinkedHashMap<>(legacyClaims);
        claims.put("summer_managed_grants",true);
        claims.put("enterprise_capabilities",Map.of());
        claims.put("platform_capabilities",List.of());
        List<Map<String,Object>> visibleMemberships=new ArrayList<>();
        for(var row:jdbc.queryForList("select m.enterprise_id from summer_enterprise_member m join summer_enterprise e on e.enterprise_id=m.enterprise_id join summer_authorization_domain d on d.domain_key=concat('ENTERPRISE:',m.enterprise_id) where m.subject=? and m.status='ACTIVE' and e.status='ACTIVE' and d.engine_mode='MANAGED'",username))
            visibleMemberships.add(Map.of("enterpriseId",row.get("enterprise_id"),"status","ACTIVE"));
        claims.put("enterprise_memberships",visibleMemberships);
        claims.remove("organization_id");
        claims.remove("organization_context");
        claims.remove("active_enterprise_id");
        var contexts = jdbc.queryForList("select c.enterprise_id, c.organization_id from summer_organization_context c join summer_organization_unit u on u.unit_id=c.organization_id and u.enterprise_id=c.enterprise_id join summer_enterprise_member m on m.enterprise_id=c.enterprise_id and m.subject=c.subject join summer_organization_membership a on a.enterprise_id=c.enterprise_id and a.member_id=m.member_id and a.organization_id=c.organization_id where c.subject=? and u.status='ACTIVE' and m.status='ACTIVE'", username);
        Object raw = claims.get("enterprise_memberships");
        for (var context : contexts) {
            if (raw instanceof Collection<?> memberships && memberships.stream().filter(Map.class::isInstance)
                    .map(Map.class::cast).anyMatch(m -> context.get("enterprise_id").equals(m.get("enterpriseId")) && "ACTIVE".equals(m.get("status")))) {
                claims.put("organization_id", context.get("organization_id"));
                claims.put("organization_context", Map.of("enterpriseId", context.get("enterprise_id"), "organizationId", context.get("organization_id")));
                claims.put("active_enterprise_id", context.get("enterprise_id"));
            }
        }
        var platform = claims.get("platform_capabilities");
        claims.put("release_operator", platform instanceof Collection<?> values && values.contains("internal.release.read"));
        claims.put("summer_identity_source", "local-user");
        return claims;
    }

    static List<String> lines(Object value) {
        if (value == null || value.toString().isBlank()) return List.of();
        if (value.toString().stripLeading().startsWith("[")) {
            var node = new tools.jackson.databind.ObjectMapper().readTree(value.toString());
            List<String> values = new ArrayList<>();
            for (var item : node) {
                if (!item.isString()) throw new IllegalStateException("Invalid persisted grant list");
                if (!item.asText().isBlank()) values.add(item.asText());
            }
            return List.copyOf(values);
        }
        return Arrays.stream(value.toString().split("\n")).filter(s -> !s.isBlank()).toList();
    }

    static List<String> capabilities(Map<String, Object> claims) {
        Set<String> values = new TreeSet<>();
        if (claims.get("platform_capabilities") instanceof Collection<?> platform) platform.forEach(c -> values.add(c.toString()));
        if (claims.get("enterprise_capabilities") instanceof Map<?, ?> enterprises) enterprises.values().forEach(raw -> {
            if (raw instanceof Collection<?> list) list.forEach(c -> values.add(c.toString()));
        });
        return List.copyOf(values);
    }
}
