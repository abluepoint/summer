package com.abluepoint.summer.sample.configuration.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ConfigurationOverrideRevisionRepository extends JpaRepository<ConfigurationOverrideRevisionEntity,String> {
    public List<ConfigurationOverrideRevisionEntity> findByTenantIdAndOverrideIdOrderByRevisionDesc(String tenant,String overrideId);
    public Optional<ConfigurationOverrideRevisionEntity> findByTenantIdAndOverrideIdAndRevision(String tenant,String overrideId,long revision);
}
