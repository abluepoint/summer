package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Uses the primary transactional database, never a Redis or token version. */
@Service
public class AuthorizationDomainVersions {
    private final JdbcTemplate jdbc;
    AuthorizationDomainVersions(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    record State(String mode, long revision) {}
    State current(Domain domain) {
        var rows = jdbc.query("select engine_mode, revision from summer_authorization_domain where domain_key = ?",
                (rs, n) -> new State(rs.getString(1), rs.getLong(2)), domain.key());
        return rows.isEmpty() ? new State("LEGACY", 0) : rows.getFirst();
    }
    /** Joins the business/outbox transaction: rollback cannot publish a newer revision. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void advance(String enterpriseId) {
        advance("platform".equals(enterpriseId) ? Domain.platform() : Domain.enterprise(enterpriseId));
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void advance(Domain domain) {
        jdbc.update("insert into summer_authorization_domain(domain_key, engine_mode, revision) values (?, 'LEGACY', 1) "
                + "on duplicate key update revision = revision + 1", domain.key());
    }
}
