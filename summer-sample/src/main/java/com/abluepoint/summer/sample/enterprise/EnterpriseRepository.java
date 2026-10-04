package com.abluepoint.summer.sample.enterprise;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface EnterpriseRepository extends JpaRepository<EnterpriseEntity, Long> {
    boolean existsByEnterpriseId(String enterpriseId);
    boolean existsByCodeIgnoreCase(String code);
    Optional<EnterpriseEntity> findByCodeIgnoreCase(String code);
}
