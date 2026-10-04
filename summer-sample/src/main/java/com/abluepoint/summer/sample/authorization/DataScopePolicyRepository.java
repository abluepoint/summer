package com.abluepoint.summer.sample.authorization;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

interface DataScopePolicyRepository extends JpaRepository<DataScopePolicyEntity, String> {
    Optional<DataScopePolicyEntity> findByEnterpriseIdAndResource(String enterpriseId, String resource);
}
