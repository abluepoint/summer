package com.abluepoint.summer.sample.configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
interface ConfigurationOverrideRevisionRepository extends JpaRepository<ConfigurationOverrideRevisionEntity,String> {
    List<ConfigurationOverrideRevisionEntity> findByTenantIdAndOverrideIdOrderByRevisionDesc(String tenant,String overrideId);
    Optional<ConfigurationOverrideRevisionEntity> findByTenantIdAndOverrideIdAndRevision(String tenant,String overrideId,long revision);
}
