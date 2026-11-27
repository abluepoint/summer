package com.abluepoint.summer.sample.authorization;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Organization context used by current DATA enforcement; legacy policy management is retired. */
@Service
public class DataScopeService {
    private final JdbcTemplate jdbc;

    DataScopeService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ScopeFilter filter(String enterpriseId, String resource, Authentication auth) {
        throw new AccessDeniedException("Legacy DATA range evaluation is retired; use current DATA permissions");
    }

    private String storedOrganization(String enterpriseId, String subject) {
        return jdbc.query("select organization_id from summer_organization_context where enterprise_id = ? and subject = ?",
                (rs, index) -> rs.getString(1), enterpriseId, subject).stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

    public String organization(String enterpriseId, Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwt && jwt.getToken().getClaims().get("organization_context") instanceof Map<?, ?> context) {
            Object owner = context.containsKey("enterpriseId") ? context.get("enterpriseId") : context.get("enterprise_id");
            if (owner != null && !enterpriseId.equals(owner.toString())) throw new AccessDeniedException("Organization context belongs to another enterprise");
        }
        String org = organizationId(auth);
        if (org == null && auth != null) org = storedOrganization(enterpriseId, principal(auth));
        assertOrganization(enterpriseId, org);
        return org;
    }

    public void assertOrganization(String enterpriseId, String organizationId) {
        if (organizationId != null && jdbc.queryForObject("select count(*) from summer_organization_unit where enterprise_id=? and unit_id=? and status='ACTIVE'",
                Long.class, enterpriseId, organizationId) != 1) throw new AccessDeniedException("Organization does not belong to the active enterprise");
    }

    private static String principal(Authentication auth) { return auth == null || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken ? null : auth.getName(); }

    public static String organizationId(Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwt)) return null;
        var claims = jwt.getToken().getClaims();
        for (String key : List.of("organization_id", "organizationId", "current_organization_id")) { Object value = claims.get(key); if (value != null) return value.toString(); }
        Object context = claims.get("organization_context");
        if (context instanceof Map<?, ?> map && map.get("organizationId") != null) return map.get("organizationId").toString();
        return null;
    }

    public static final class ScopeFilter {
        private final String enterpriseId;
        private final DataScopeExpression.Node expression;
        private final String fingerprint;
        private ScopeFilter(String enterpriseId, DataScopeExpression.Node expression, String policyChecksum) {
            this.enterpriseId = enterpriseId;
            this.expression = expression;
            try {
                this.fingerprint = policyChecksum + ":" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(expression.toString().getBytes(StandardCharsets.UTF_8)));
            } catch (java.security.NoSuchAlgorithmException failure) {
                throw new IllegalStateException("scope fingerprint unavailable", failure);
            }
        }
        public boolean allows(String rowOrg, String rowCreator, String rowOwner, String rowSubject) {
            return expression.matches(new DataScopeExpression.Row(enterpriseId, rowOrg, rowCreator, rowOwner, rowSubject));
        }
        public <T> org.springframework.data.jpa.domain.Specification<T> specification() {
            return DataScopeExpression.specification(expression);
        }
        public String sqlPredicate() { return DataScopeExpression.sql(expression).predicate(); }
        public Map<String, Object> sqlBindings() { return DataScopeExpression.sql(expression).bindings(); }
        public String fingerprint() { return fingerprint; }
    }
}
