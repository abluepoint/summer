package com.abluepoint.summer.sample.authorization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface AuthorizationAuditRepository extends JpaRepository<AuthorizationAuditEntity, Long> {
    public List<AuthorizationAuditEntity> findByEnterpriseIdAndResourceIdInOrderByAuditIdAsc(String enterpriseId, Collection<String> resourceIds);
}
