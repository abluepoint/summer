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
        if (!java.util.Set.of("create", "update", "delete").contains(action))
            throw SummerException.withMessage(
                    PermissionErrorCode.INVALID, "Registered write action required");
        if (!access.authenticated(actor))
            throw new AccessDeniedException("Authenticated writer required");
        try {
            return writes.execute(
                    tx -> {
                        if (db.queryForList(
                                        "select revision from summer_authorization_domain where"
                                            + " domain_key=? for update",
                                        domain.key())
                                .isEmpty())
                            throw new AccessDeniedException("Unknown authorization domain");
                        // The scope is computed on its own read-only connection; mutation uses this
                        // writable transaction.
                        var scope = reads.read(domain, resource, action, actor, current -> current);
                        return mutation.apply(scope);
                    });
        } catch (DataAccessException | TransactionException failure) {
            throw SummerException.withMessage(
                    SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE,
                    "Current data operation unavailable",
                    failure);
        }
    }
}
