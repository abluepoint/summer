package com.abluepoint.summer.sample.auditledger.infrastructure;
import com.abluepoint.summer.sample.auditledger.infrastructure.AuditOutboxStatus;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class JpaAuditOutboxStore implements AuditOutboxStore {
    private final AuditOutboxRepository repository;

    public JpaAuditOutboxStore(AuditOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    public AuditOutboxEntity enqueue(AuditOutboxEntity message) {
        return repository.save(message);
    }

    @Override
    public List<AuditOutboxEntity> findDue(Instant now, int limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        return repository.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(AuditOutboxStatus.PENDING, now)
                .stream().limit(limit).toList();
    }

    @Override
    public void markPublished(long id) {
        repository.findById(id).ifPresent(entity -> {
            entity.markPublished();
            repository.save(entity);
        });
    }

    @Override
    public void reschedule(long id, Instant nextAttemptAt) {
        repository.findById(id).ifPresent(entity -> {
            entity.reschedule(nextAttemptAt);
            repository.save(entity);
        });
    }

    @Override
    public void deadLetter(long id) {
        repository.findById(id).ifPresent(entity -> {
            entity.deadLetter();
            repository.save(entity);
        });
    }
}
