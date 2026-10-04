package com.abluepoint.summer.sample.systemparameter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SystemParameterVersionRepository extends JpaRepository<SystemParameterVersionEntity, String> {
    List<SystemParameterVersionEntity> findAllByTenantIdAndParameterKeyOrderByRevisionAsc(String tenantId, String parameterKey);
    Optional<SystemParameterVersionEntity> findByTenantIdAndParameterKeyAndRevision(String tenantId, String parameterKey, long revision);
    Optional<SystemParameterVersionEntity> findByTenantIdAndParameterKeyAndIdempotencyKey(String tenantId, String parameterKey, String idempotencyKey);
    Optional<SystemParameterVersionEntity> findTopByTenantIdAndParameterKeyOrderByRevisionDesc(String tenantId, String parameterKey);
}
