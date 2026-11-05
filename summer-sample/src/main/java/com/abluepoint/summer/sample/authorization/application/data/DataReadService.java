package com.abluepoint.summer.sample.authorization.application.data;

import com.abluepoint.summer.sample.authorization.application.permission.ManagedPermissionAccess;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.function.Function;

/** An immutable range is consumed within the same fresh read as its authority revision. */
@Service
public class DataReadService {
    private final JdbcTemplate db;
    private final ManagedPermissionAccess access;
    private final DataResourceRegistry registry;
    private final TransactionTemplate fresh;

    public DataReadService(
            JdbcTemplate db,
            ManagedPermissionAccess access,
            DataResourceRegistry registry,
            PlatformTransactionManager tm) {
        this.db = db;
        this.access = access;
        this.registry = registry;
        fresh = new TransactionTemplate(tm);
        fresh.setReadOnly(true);
        fresh.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        fresh.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public record Scope(
            Domain domain,
            DataResourceRegistry.Resource resource,
            long revision,
            boolean all,
            boolean self,
            String subject) {
        public String predicate() {
            return "domain_key=:scopeDomain and "
                    + resource.tenantColumn()
                    + "=:scopeTenant and "
                    + resource.upperBoundColumn()
                    + "=true and "
                    + (all ? "1=1" : self ? resource.selfColumn() + "=:scopeSubject" : "1=0");
        }

        public Map<String, Object> bindings() {
            var b = new HashMap<String, Object>();
            b.put("scopeTenant", domain.id());
            b.put("scopeDomain", domain.key());
            if (self && !all) b.put("scopeSubject", subject);
            return b;
        }

        public <T> Specification<T> specification() {
            return (root, q, cb) ->
                    cb.and(
                            cb.equal(root.get("domainKey"), domain.key()),
                            cb.equal(root.get(resource.tenantAttribute()), domain.id()),
                            cb.isTrue(root.get(resource.upperBoundAttribute())),
                            all
                                    ? cb.conjunction()
                                    : self
                                            ? cb.equal(root.get(resource.selfAttribute()), subject)
                                            : cb.disjunction());
        }
    }

    public <T> T read(
            Domain d, String resource, String action, Authentication a, Function<Scope, T> query) {
        var registration = registry.require(resource, action);
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                T result =
                        fresh.execute(
                                tx -> {
                                    var before = state(d);
                                    if (!"MANAGED".equals(before.get("engine_mode")))
                                        throw new AccessDeniedException(
                                                "Managed data domain required");
                                    if (d.kind().equals("ENTERPRISE")
                                            && db.queryForObject(
                                                            "select count(*) from summer_enterprise"
                                                                + " where enterprise_id=? and"
                                                                + " status='ACTIVE'",
                                                            Integer.class,
                                                            d.id())
                                                    == 0)
                                        throw new AccessDeniedException(
                                                "Active enterprise required");
                                    if (access.authenticated(a) && !access.active(d, a))
                                        throw new AccessDeniedException("Active identity required");
                                    boolean all = false, self = false;
                                    for (var p :
                                            db.queryForList(
                                                    "select p.*,s.scope_preset from"
                                                        + " summer_permission p join"
                                                        + " summer_permission_data_scope s on"
                                                        + " s.domain_key=p.domain_key and"
                                                        + " s.permission_id=p.permission_id where"
                                                        + " p.domain_key=? and p.type='DATA' and"
                                                        + " s.data_resource_key=? and s.action=?",
                                                    d.key(),
                                                    resource,
                                                    action)) {
                                        if (!access.applies(d, a, p)) continue;
                                        if ("TENANT_ALL".equals(p.get("scope_preset"))) all = true;
                                        else if ("SELF".equals(p.get("scope_preset"))
                                                && access.active(d, a)) self = true;
                                    }
                                    T value =
                                            query.apply(
                                                    new Scope(
                                                            d,
                                                            registration,
                                                            ((Number) before.get("revision"))
                                                                    .longValue(),
                                                            all,
                                                            self,
                                                            a == null ? null : a.getName()));
                                    return before.equals(state(d)) ? value : null;
                                });
                if (result != null) return result;
            }
            throw new AccessDeniedException("Data authorization changed during read");
        } catch (org.springframework.dao.DataAccessException
                | org.springframework.transaction.TransactionException e) {
            throw com.abluepoint.summer.common.error.SummerException.withMessage(
                    com.abluepoint.summer.sample.error.SampleTransportErrorCode
                            .DEPENDENCY_UNAVAILABLE,
                    "Current data authorization unavailable");
        }
    }

    private Map<String, Object> state(Domain d) {
        var rows =
                db.queryForList(
                        "select engine_mode,revision from summer_authorization_domain where"
                            + " domain_key=?",
                        d.key());
        if (rows.isEmpty()) throw new AccessDeniedException("Unknown authorization domain");
        return rows.getFirst();
    }
}
