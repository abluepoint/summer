package com.abluepoint.summer.sample.identity;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves the local console's business grants independently of requested OAuth scopes. */
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
        var bindings = jdbc.queryForList("select enterprise_id, role_ids, denied_capability_codes from summer_authorization_user_binding where username = ?", username);
        Map<String, Object> claims = new LinkedHashMap<>(legacyClaims);
        if (!bindings.isEmpty() || Boolean.TRUE.equals(claims.get("summer_managed_grants"))) {
            claims.put("summer_managed_grants", true);
            List<Map<String, Object>> memberships = new ArrayList<>();
            Map<String, List<String>> enterpriseGrants = new LinkedHashMap<>();
            Set<String> platformGrants = new TreeSet<>();
            for (var binding : bindings) {
                String enterprise = String.valueOf(binding.get("enterprise_id"));
                var active = jdbc.queryForList("select e.enterprise_id from summer_enterprise e join summer_enterprise_member m on m.enterprise_id=e.enterprise_id where e.enterprise_id=? and e.status='ACTIVE' and m.subject=? and m.status='ACTIVE'", String.class, enterprise, username);
                if (active.isEmpty()) continue;
                memberships.add(Map.of("enterpriseId", enterprise, "status", "ACTIVE"));
                Set<String> denied = new HashSet<>(lines(binding.get("denied_capability_codes")));
                Set<String> granted = new TreeSet<>();
                for (String role : lines(binding.get("role_ids"))) {
                    for (String ids : jdbc.queryForList("select capability_ids from summer_authorization_role where role_id = ? and enterprise_id = ? and status = 'ACTIVE'", String.class, role, enterprise)) {
                        for (String id : lines(ids)) {
                            for (var cap : jdbc.queryForList("select code, scope from summer_authorization_capability where capability_id = ? and status = 'ACTIVE' and (enterprise_id = ? or (enterprise_id is null and scope = 'PLATFORM'))", id, enterprise)) {
                                String code = String.valueOf(cap.get("code"));
                                if (denied.contains(code)) continue;
                                if ("PLATFORM".equals(cap.get("scope"))) platformGrants.add(code); else granted.add(code);
                            }
                        }
                    }
                }
                enterpriseGrants.put(enterprise, List.copyOf(granted));
            }
            claims.put("enterprise_memberships", memberships);
            claims.put("enterprise_capabilities", enterpriseGrants);
            claims.put("platform_capabilities", List.copyOf(platformGrants));
        }
        if (!Boolean.TRUE.equals(claims.get("summer_managed_grants"))) {
            boolean bootstrap = username.equals(bootstrapUsername) && !jdbc.queryForList("select m.member_id from summer_enterprise_member m join summer_enterprise e on e.enterprise_id=m.enterprise_id where m.enterprise_id='ent-acme' and m.subject=? and m.status='ACTIVE' and e.status='ACTIVE'", String.class, username).isEmpty();
            if (!bootstrap) {
                claims.put("enterprise_memberships", List.of());
                claims.put("enterprise_capabilities", Map.of());
                claims.put("platform_capabilities", List.of());
            }
        }
        // New-mode memberships come from current membership facts, independently of legacy grants.
        var managedDomains=jdbc.queryForList("select domain_key from summer_authorization_domain where engine_mode='MANAGED' and domain_key like 'ENTERPRISE:%'",String.class);
        List<Map<String,Object>> visibleMemberships=new ArrayList<>();
        if(claims.get("enterprise_memberships") instanceof Collection<?> values)for(Object value:values) {
            if(value instanceof Map<?,?> member && !managedDomains.contains("ENTERPRISE:"+member.get("enterpriseId"))) {
                Map<String,Object> copy=new LinkedHashMap<>();member.forEach((k,v)->copy.put(k.toString(),v));visibleMemberships.add(copy);
            }
        }
        for(var row:jdbc.queryForList("select m.enterprise_id from summer_enterprise_member m join summer_enterprise e on e.enterprise_id=m.enterprise_id join summer_authorization_domain d on d.domain_key=concat('ENTERPRISE:',m.enterprise_id) where m.subject=? and m.status='ACTIVE' and e.status='ACTIVE' and d.engine_mode='MANAGED'",username))visibleMemberships.add(Map.of("enterpriseId",row.get("enterprise_id"),"status","ACTIVE"));
        claims.put("enterprise_memberships",visibleMemberships);
        // Managed grants are read through current permission resources, never reissued as old
        // capability authorities. Keep only explicit LEGACY domain projections during expand.
        if (claims.get("enterprise_capabilities") instanceof Map<?,?> prior) {
            Map<String,Object> legacyEnterpriseGrants=new LinkedHashMap<>();
            prior.forEach((key,value) -> {
                if (!managedDomains.contains("ENTERPRISE:"+key)) legacyEnterpriseGrants.put(key.toString(),value);
            });
            claims.put("enterprise_capabilities",legacyEnterpriseGrants);
        }
        if (jdbc.queryForObject("select count(*) from summer_authorization_domain where domain_key='PLATFORM:platform' and engine_mode='MANAGED'",Integer.class)>0)
            claims.put("platform_capabilities",List.of());

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
