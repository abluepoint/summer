package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.function.LongFunction;

/** Uses the primary transactional database, never a Redis or token version. */
@Service
public class AuthorizationDomainVersions {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate predicate;
    private final TransactionTemplate diagnostic;
    AuthorizationDomainVersions(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        predicate = fresh(transactions); predicate.setReadOnly(true);
        diagnostic = fresh(transactions);
    }
    private static TransactionTemplate fresh(PlatformTransactionManager transactions) {
        var tx = new TransactionTemplate(transactions);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx;
    }
    record State(String mode, long revision, Long catalogRevision) {}
    State current(Domain domain) {
        var rows = jdbc.query("select engine_mode, revision from summer_authorization_domain where domain_key = ?",
                (rs, n) -> {
                    String mode = rs.getString(1);
                    long revision = rs.getLong(2);
                    if (rs.wasNull() || revision < 0 || !("LEGACY".equals(mode) || "MANAGED".equals(mode)))
                        throw new AccessDeniedException("Unconfirmed authorization domain state");
                    return new State(mode, revision, null);
                }, domain.key());
        if (rows.size() != 1) throw new AccessDeniedException("Unconfirmed authorization domain state");
        var state = rows.getFirst();
        if (!Domain.platform().equals(domain) && "MANAGED".equals(state.mode())) {
            var catalog = jdbc.queryForList("select revision from summer_authorization_domain where domain_key='PLATFORM:platform' and engine_mode='MANAGED'", Long.class);
            return new State(state.mode(), state.revision(), catalog.isEmpty() ? null : catalog.getFirst());
        }
        return state;
    }
    /** False means an explicitly persisted LEGACY mode, never absent or unknown state. */
    public boolean managed(Domain domain) {
        try { return Boolean.TRUE.equals(predicate.execute(tx -> "MANAGED".equals(current(domain).mode()))); }
        catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException e) {
            throw unavailable();
        }
    }
    private record Read<T>(boolean stable, T value) {}
    /**
     * Diagnostic reads may take existing catalog/publication locks, so this RC transaction is not
     * read-only. Callbacks must not update the domain or execute business DML, and may return only
     * materialized projections. Catalog preparation happens before this transaction; no domain
     * write lock is acquired after a catalog lock.
     */
    public <T> T readManaged(Domain domain, LongFunction<T> query) {
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                Read<T> read = diagnostic.execute(tx -> {
                    var before = current(domain);
                    if (!"MANAGED".equals(before.mode()))
                        throw new AccessDeniedException("Managed authorization domain required");
                    T value = query.apply(before.revision());
                    return new Read<>(before.equals(current(domain)), value);
                });
                if (read != null && read.stable()) return read.value();
            }
            throw new AccessDeniedException("Authorization changed during diagnostic read");
        } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException e) {
            throw unavailable();
        }
    }
    private static com.abluepoint.summer.common.error.SummerException unavailable() {
        return com.abluepoint.summer.common.error.SummerException.withMessage(
                com.abluepoint.summer.sample.error.SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE,
                "The authoritative authorization store is unavailable");
    }
    /** Joins the business/outbox transaction: rollback cannot publish a newer revision. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void advance(Domain domain) {
        if (jdbc.update("update summer_authorization_domain set revision=revision+1 where domain_key=?"
                + " and engine_mode in ('LEGACY','MANAGED') and revision>=0", domain.key()) != 1)
            throw new AccessDeniedException("Unconfirmed authorization domain state");
    }
}
