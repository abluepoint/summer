package com.abluepoint.summer.sample.configuration.infrastructure.persistence;
import org.springframework.data.jpa.repository.*;
public interface ConfigurationOverrideRepository extends JpaRepository<ConfigurationOverrideEntity,String>, JpaSpecificationExecutor<ConfigurationOverrideEntity> {
    public boolean existsByTenantIdAndResourceAndKeyAndScopeId(String tenant,String resource,String key,String scopeId);
    public boolean existsByTenantIdAndResourceAndKeyAndScopeIdAndPublishedRevisionIsNotNull(String tenant,String resource,String key,String scopeId);
}
