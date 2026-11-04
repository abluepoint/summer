package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

import java.util.*;

/** Expand-only management boundary: persisted legacy grants, never claims as business authority. */
@Service
public class PermissionManagementGuard {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PermissionManagementGuard(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void require(Domain domain, Authentication auth, boolean write) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken)
            throw new AccessDeniedException("Authentication required");
        String code =
                (domain.kind().equals("PLATFORM") ? "platform.capabilities." : "enterprise.roles.")
                        + (write ? "write" : "read");
        if (auth instanceof JwtAuthenticationToken
                && auth.getAuthorities().stream()
                        .noneMatch(x -> x.getAuthority().equals("SCOPE_" + code)))
            throw new AccessDeniedException("Delegated management scope required");
        var bindings =
                jdbc.queryForList(
                        "select b.* from summer_authorization_user_binding b join"
                            + " summer_enterprise_member m on m.enterprise_id=b.enterprise_id and"
                            + " m.subject=b.username join summer_enterprise e on"
                            + " e.enterprise_id=m.enterprise_id where b.username=? and"
                            + " m.status='ACTIVE' and e.status='ACTIVE'",
                        auth.getName());
        for (var b : bindings) {
            String enterprise = String.valueOf(b.get("enterprise_id"));
            if (domain.kind().equals("ENTERPRISE") && !domain.id().equals(enterprise)) continue;
            if (strings(b.get("denied_capability_codes")).contains(code)) continue;
            for (String role : strings(b.get("role_ids"))) {
                var roles =
                        jdbc.queryForList(
                                "select capability_ids from summer_authorization_role where"
                                    + " role_id=? and enterprise_id=? and status='ACTIVE'",
                                role,
                                enterprise);
                for (var r : roles)
                    for (String id : strings(r.get("capability_ids"))) {
                        var caps =
                                jdbc.queryForList(
                                        "select scope,enterprise_id,code,status from"
                                            + " summer_authorization_capability where"
                                            + " capability_id=?",
                                        id);
                        for (var c : caps)
                            if ("ACTIVE".equals(c.get("status"))
                                    && code.equals(c.get("code"))
                                    && domain.kind().equals(c.get("scope"))
                                    && (domain.kind().equals("PLATFORM")
                                            ? c.get("enterprise_id") == null
                                            : enterprise.equals(c.get("enterprise_id")))) return;
                    }
            }
        }
        throw new AccessDeniedException("Persistent management grant required");
    }

    private List<String> strings(Object value) {
        if (value == null) return List.of();
        String s =
                value instanceof byte[] b
                        ? new String(b, java.nio.charset.StandardCharsets.UTF_8)
                        : value.toString();
        if (s.isBlank()) return List.of();
        try {
            if (s.startsWith("[")) return json.readValue(s, List.class);
            return List.of(s.split("\n"));
        } catch (RuntimeException ex) {
            throw new AccessDeniedException("Invalid persisted management grant");
        }
    }
}
