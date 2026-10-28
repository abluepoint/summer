package com.abluepoint.summer.sample.customer.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Resolves the current, persisted selection rather than a possibly stale token claim. */
@Component
final class AuditOrganizationContext {
    private final JdbcTemplate jdbc;

    AuditOrganizationContext(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    String current(String enterpriseId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken || authentication.getName() == null) {
            return null;
        }
        var selections = jdbc.queryForList("""
                SELECT c.organization_id
                  FROM summer_organization_context c
                  JOIN summer_enterprise_member m
                    ON m.enterprise_id = c.enterprise_id AND m.subject = c.subject AND m.status = 'ACTIVE'
                  JOIN summer_organization_membership a
                    ON a.enterprise_id = m.enterprise_id AND a.member_id = m.member_id
                   AND a.organization_id = c.organization_id
                  JOIN summer_organization_unit u
                    ON u.enterprise_id = c.enterprise_id AND u.unit_id = c.organization_id AND u.status = 'ACTIVE'
                  JOIN summer_enterprise e
                    ON e.enterprise_id = c.enterprise_id AND e.status = 'ACTIVE'
                 WHERE c.enterprise_id = ? AND c.subject = ?
                """, String.class, enterpriseId, authentication.getName());
        return selections.size() == 1 ? selections.getFirst() : null;
    }
}
