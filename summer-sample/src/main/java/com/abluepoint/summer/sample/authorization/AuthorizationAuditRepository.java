package com.abluepoint.summer.sample.authorization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface AuthorizationAuditRepository extends JpaRepository<AuthorizationAuditEntity, Long> {
    List<AuthorizationAuditEntity> findByEnterpriseIdAndResourceIdInOrderByAuditIdAsc(String enterpriseId, Collection<String> resourceIds);
}
