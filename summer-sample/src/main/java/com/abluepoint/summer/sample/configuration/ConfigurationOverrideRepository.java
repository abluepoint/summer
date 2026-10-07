package com.abluepoint.summer.sample.configuration;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
interface ConfigurationOverrideRepository extends JpaRepository<ConfigurationOverrideEntity,String>, JpaSpecificationExecutor<ConfigurationOverrideEntity> {
    boolean existsByTenantIdAndResourceAndKeyAndScopeId(String tenant,String resource,String key,String scopeId);
}
