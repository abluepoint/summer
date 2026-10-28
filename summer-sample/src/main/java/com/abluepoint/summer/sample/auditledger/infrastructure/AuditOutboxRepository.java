package com.abluepoint.summer.sample.auditledger.infrastructure;
import com.abluepoint.summer.sample.auditledger.infrastructure.AuditOutboxStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AuditOutboxRepository extends JpaRepository<AuditOutboxEntity, Long> {
    List<AuditOutboxEntity> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            AuditOutboxStatus status,
            Instant now);
}
