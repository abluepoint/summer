package com.abluepoint.summer.sample.enterprise;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface EnterpriseRepository extends JpaRepository<EnterpriseEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from EnterpriseEntity e where e.enterpriseId = :enterpriseId")
    Optional<EnterpriseEntity> lockByEnterpriseId(@org.springframework.data.repository.query.Param("enterpriseId") String enterpriseId);
    boolean existsByEnterpriseId(String enterpriseId);
    boolean existsByCodeIgnoreCase(String code);
    Optional<EnterpriseEntity> findByEnterpriseId(String enterpriseId);
    Optional<EnterpriseEntity> findByCodeIgnoreCase(String code);
}
