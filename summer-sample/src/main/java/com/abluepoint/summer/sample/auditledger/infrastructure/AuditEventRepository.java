package com.abluepoint.summer.sample.auditledger.infrastructure;


import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String> {
    Optional<AuditEventEntity> findByEventId(String eventId);
    Optional<AuditEventEntity> findTopByEnterpriseIdAndInvocationIdAndAttemptIdOrderByOccurredAtDesc(
            String enterpriseId, String invocationId, String attemptId);
    Optional<AuditEventEntity> findTopByInvocationIdAndAttemptIdOrderByOccurredAtDesc(String invocationId, String attemptId);
    List<AuditEventEntity> findTop500ByOccurredAtBeforeOrderByOccurredAtAsc(Instant cutoff);
}
