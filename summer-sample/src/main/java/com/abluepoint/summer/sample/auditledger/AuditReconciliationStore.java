package com.abluepoint.summer.sample.auditledger;

import java.util.Optional;

/** Read boundary for late/uncertain physical execution receipts. */
public interface AuditReconciliationStore {
    Optional<AuditEventEntity> findLatestExecution(String enterpriseId, String invocationId, String attemptId);

    /** @deprecated use tenant-bound overload to prevent cross-enterprise lookups. */
    @Deprecated
    Optional<AuditEventEntity> findLatestExecution(String invocationId, String attemptId);
}
