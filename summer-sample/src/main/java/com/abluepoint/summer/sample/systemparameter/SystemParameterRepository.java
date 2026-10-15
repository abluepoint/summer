package com.abluepoint.summer.sample.systemparameter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface SystemParameterRepository extends JpaRepository<SystemParameterEntity, String>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<SystemParameterEntity> {
    boolean existsByTenantIdAndKey(String tenantId, String key);
    List<SystemParameterEntity> findAllByTenantIdOrderByKeyAsc(String tenantId);
    default Optional<SystemParameterEntity> findScoped(String key, com.abluepoint.summer.sample.authorization.DataScopeService.ScopeFilter scope) {
        return findOne(scope.<SystemParameterEntity>specification().and((root, query, builder) -> builder.equal(root.get("key"), key)));
    }
}
