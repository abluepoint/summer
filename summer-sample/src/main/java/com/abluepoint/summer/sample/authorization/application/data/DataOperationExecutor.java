package com.abluepoint.summer.sample.authorization.application.data;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.application.permission.ManagedPermissionAccess;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Function;

/** Serializes a range-guarded write with changes to the domain's authorization facts. */
@Service
public class DataOperationExecutor {
    private final JdbcTemplate db;
    private final DataReadService reads;
    private final ManagedPermissionAccess access;
    private final TransactionTemplate writes;

    public DataOperationExecutor(
            JdbcTemplate db,
            DataReadService reads,
            ManagedPermissionAccess access,
            PlatformTransactionManager transactions) {
        this.db = db;
        this.reads = reads;
        this.access = access;
        writes = new TransactionTemplate(transactions);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * The callback must put Scope.predicate/bindings into the actual DML, and throw on mismatch.
     */
    public <T> T write(
            Domain domain,
            String resource,
            String action,
            Authentication actor,
            Function<DataReadService.Scope, T> mutation) {
        return write(domain, resource, java.util.Set.of(action), actor,
                scopes -> mutation.apply(scopes.get(action)));
    }

    /**
     * All actions share one domain lock and one writable transaction. Callers must apply each
     * corresponding scope to its actual DML and throw if any target cannot be changed.
     */
    public <T> T write(
            Domain domain,
            String resource,
            java.util.Set<String> actions,
            Authentication actor,
            Function<java.util.Map<String, DataReadService.Scope>, T> mutation) {
        return write(domain, resource, actions, actor, mutation,
                () -> { throw new AccessDeniedException("Managed data domain required"); });
    }

    /** Explicit expand adapter; the persisted mode is read under the same domain write lock. */
    public <T> T write(
            Domain domain,
            String resource,
            java.util.Set<String> actions,
            Authentication actor,
            Function<java.util.Map<String, DataReadService.Scope>, T> mutation,
            java.util.function.Supplier<T> explicitLegacy) {
        if (actions == null || actions.isEmpty()
                || !java.util.Set.of("create", "update", "delete").containsAll(actions))
            throw SummerException.withMessage(
                    PermissionErrorCode.INVALID, "Registered write actions required");
        var fixed = java.util.Set.copyOf(actions);
        return writeResolved(domain, resource, () -> fixed, actor, mutation, ignored -> explicitLegacy.get());
    }

    /** Resolve actual insert/update/delete work only after serializing the business domain. */
    public <T> T writeResolved(Domain domain, String resource,
            java.util.function.Supplier<java.util.Set<String>> resolveActions, Authentication actor,
            Function<java.util.Map<String, DataReadService.Scope>, T> mutation,
            Function<java.util.Set<String>, T> explicitLegacy) {
        java.util.Objects.requireNonNull(domain);
        java.util.Objects.requireNonNull(resource);
        java.util.Objects.requireNonNull(mutation);
        if (!access.authenticated(actor))
            throw new AccessDeniedException("Authenticated writer required");
        try {
            return writes.execute(
                    tx -> {
                        var domainRows = db.queryForList(
                                        "select engine_mode,revision from summer_authorization_domain where"
                                            + " domain_key=? for update",
                                        domain.key());
                        if (domainRows.size() != 1)
                            throw new AccessDeniedException("Unknown authorization domain");
                        var domainState = domainRows.getFirst();
                        Object storedMode = domainState.get("engine_mode");
                        if (!(domainState.get("revision") instanceof Number revisionValue)
                                || revisionValue.longValue() < 0
                                || !(storedMode instanceof String))
                            throw new AccessDeniedException("Unconfirmed authorization domain state");
                        String mode = (String) storedMode;
                        if (!java.util.Set.of("LEGACY", "MANAGED").contains(mode))
                            throw new AccessDeniedException("Unsupported authorization mode");
                        var actions = resolveActions.get();
                        if (actions == null || actions.isEmpty() || !java.util.Set.of("create", "update", "delete").containsAll(actions))
                            throw SummerException.withMessage(PermissionErrorCode.INVALID, "Registered write actions required");
                        var orderedActions = new java.util.TreeSet<>(actions);
                        if ("LEGACY".equals(mode)) return explicitLegacy.apply(java.util.Set.copyOf(actions));
                        if (!"MANAGED".equals(mode))
                            throw new AccessDeniedException("Unsupported authorization mode");
                        // The scope is computed on its own read-only connection; mutation uses this
                        // writable transaction.
                        var scopes = new java.util.LinkedHashMap<String, DataReadService.Scope>();
                        Long revision = null;
                        for (String action : orderedActions) {
                            var scope = reads.read(domain, resource, action, actor, current -> current);
                            if (revision != null && revision.longValue() != scope.revision())
                                throw new AccessDeniedException("Data authorization changed during mutation");
                            revision = scope.revision();
                            scopes.put(action, scope);
                        }
                        return mutation.apply(java.util.Collections.unmodifiableMap(scopes));
                    });
        } catch (DataAccessException | TransactionException failure) {
            throw SummerException.withMessage(
                    SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE,
                    "Current data operation unavailable",
                    failure);
        }
    }
}
