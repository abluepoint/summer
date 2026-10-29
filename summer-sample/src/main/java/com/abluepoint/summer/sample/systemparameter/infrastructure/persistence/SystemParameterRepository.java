package com.abluepoint.summer.sample.systemparameter.infrastructure.persistence;

import com.abluepoint.summer.sample.authorization.application.DataScopeService;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemParameterRepository extends JpaRepository<SystemParameterEntity, String>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<SystemParameterEntity> {
    public boolean existsByTenantIdAndKey(String tenantId, String key);
    public List<SystemParameterEntity> findAllByTenantIdOrderByKeyAsc(String tenantId);
    public default Optional<SystemParameterEntity> findScoped(String key, com.abluepoint.summer.sample.authorization.application.DataScopeService.ScopeFilter scope) {
        return findOne(scope.<SystemParameterEntity>specification().and((root, query, builder) -> builder.equal(root.get("key"), key)));
    }
}
