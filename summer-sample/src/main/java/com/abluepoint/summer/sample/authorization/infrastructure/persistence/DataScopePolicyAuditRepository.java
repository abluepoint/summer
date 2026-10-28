package com.abluepoint.summer.sample.authorization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DataScopePolicyAuditRepository extends JpaRepository<DataScopePolicyAuditEntity, String> {
    public List<DataScopePolicyAuditEntity> findByEnterpriseIdAndResourceOrderByOccurredAtDesc(String enterpriseId, String resource);
}
