package com.abluepoint.summer.sample.authorization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface DataScopePolicyAuditRepository extends JpaRepository<DataScopePolicyAuditEntity, String> {
    List<DataScopePolicyAuditEntity> findByEnterpriseIdAndResourceOrderByOccurredAtDesc(String enterpriseId, String resource);
}
