package com.abluepoint.summer.sample.systemparameter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface SystemParameterRepository extends JpaRepository<SystemParameterEntity, String> {
    Optional<SystemParameterEntity> findByKey(String key);
    boolean existsByKey(String key);
    List<SystemParameterEntity> findAllByTenantIdOrderByKeyAsc(String tenantId);
    @Query("select p from SystemParameterEntity p where p.tenantId = :tenant and (:tenantScope = true or (:subject is not null and ((:selfScope = true and p.createdBy = :subject) or (:creatorScope = true and p.createdBy = :subject) or (:ownerScope = true and p.ownerId = :subject) or (:orgScope = true and p.organizationId = :organization)))) order by p.key asc")
    List<SystemParameterEntity> findScoped(@Param("tenant") String tenant, @Param("tenantScope") boolean tenantScope, @Param("subject") String subject, @Param("selfScope") boolean selfScope, @Param("creatorScope") boolean creatorScope, @Param("ownerScope") boolean ownerScope, @Param("orgScope") boolean orgScope, @Param("organization") String organization);
}
