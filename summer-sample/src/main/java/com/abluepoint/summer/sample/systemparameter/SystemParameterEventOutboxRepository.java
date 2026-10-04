package com.abluepoint.summer.sample.systemparameter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

interface SystemParameterEventOutboxRepository extends JpaRepository<SystemParameterEventOutboxEntity, String> {
    List<SystemParameterEventOutboxEntity> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            SystemParameterEventStatus status, Instant now);
    List<SystemParameterEventOutboxEntity> findAllByTenantIdAndParameterKeyOrderByCreatedAtDesc(
            String tenantId, String parameterKey);
    List<SystemParameterEventOutboxEntity> findAllByTenantIdOrderByCreatedAtDesc(String tenantId);
}
