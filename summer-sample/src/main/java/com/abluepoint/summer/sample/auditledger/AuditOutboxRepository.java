package com.abluepoint.summer.sample.auditledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

interface AuditOutboxRepository extends JpaRepository<AuditOutboxEntity, Long> {
    List<AuditOutboxEntity> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            AuditOutboxStatus status,
            Instant now);
}
