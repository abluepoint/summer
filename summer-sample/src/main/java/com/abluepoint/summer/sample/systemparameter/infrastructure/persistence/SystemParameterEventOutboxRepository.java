package com.abluepoint.summer.sample.systemparameter.infrastructure.persistence;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterEventStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SystemParameterEventOutboxRepository extends JpaRepository<SystemParameterEventOutboxEntity, String> {
    public List<SystemParameterEventOutboxEntity> findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            SystemParameterEventStatus status, Instant now);
    public List<SystemParameterEventOutboxEntity> findAllByTenantIdAndParameterKeyOrderByCreatedAtDesc(
            String tenantId, String parameterKey);
    public List<SystemParameterEventOutboxEntity> findAllByTenantIdOrderByCreatedAtDesc(String tenantId);
}
