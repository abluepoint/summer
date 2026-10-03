package com.abluepoint.summer.sample.auditledger;

import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class JpaAuditEventStore implements AuditEventStore, AuditReconciliationStore, AuditRetentionStore {
    private final AuditEventRepository repository;

    JpaAuditEventStore(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuditEventEntity append(AuditEventEntity event) {
        return repository.save(event);
    }

    @Override
    public Optional<AuditEventEntity> findByEventId(String eventId) {
        return repository.findByEventId(eventId);
    }

    @Override
    public Optional<AuditEventEntity> findLatestExecution(String enterpriseId, String invocationId, String attemptId) {
        if (enterpriseId == null || enterpriseId.isBlank()) {
            throw new IllegalArgumentException("enterpriseId is required for reconciliation");
        }
        return repository.findTopByEnterpriseIdAndInvocationIdAndAttemptIdOrderByOccurredAtDesc(
                enterpriseId, invocationId, attemptId);
    }

    @Override
    @Deprecated
    public Optional<AuditEventEntity> findLatestExecution(String invocationId, String attemptId) {
        return repository.findTopByInvocationIdAndAttemptIdOrderByOccurredAtDesc(invocationId, attemptId);
    }

    @Override
    public int purgeBefore(java.time.Instant cutoff, int batchSize) {
        throw new UnsupportedOperationException(
                "Audit ledger is append-only; retention must use an archive/partition adapter");
    }
}
