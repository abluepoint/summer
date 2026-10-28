package com.abluepoint.summer.sample.authorization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DataScopePolicyRepository extends JpaRepository<DataScopePolicyEntity, String> {
    public Optional<DataScopePolicyEntity> findByEnterpriseIdAndResource(String enterpriseId, String resource);
}
