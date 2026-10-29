package com.abluepoint.summer.sample.systemparameter.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemParameterVersionRepository extends JpaRepository<SystemParameterVersionEntity, String> {
    public List<SystemParameterVersionEntity> findAllByTenantIdAndParameterKeyOrderByRevisionAsc(String tenantId, String parameterKey);
    public Optional<SystemParameterVersionEntity> findByTenantIdAndParameterKeyAndRevision(String tenantId, String parameterKey, long revision);
    public Optional<SystemParameterVersionEntity> findByTenantIdAndParameterKeyAndIdempotencyKey(String tenantId, String parameterKey, String idempotencyKey);
    public Optional<SystemParameterVersionEntity> findTopByTenantIdAndParameterKeyOrderByRevisionDesc(String tenantId, String parameterKey);
}
