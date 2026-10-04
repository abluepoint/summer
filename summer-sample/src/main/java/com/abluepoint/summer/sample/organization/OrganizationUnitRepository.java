package com.abluepoint.summer.sample.organization;

import org.springframework.data.jpa.repository.JpaRepository;

interface OrganizationUnitRepository extends JpaRepository<OrganizationUnitEntity, Long> {
    boolean existsByUnitId(String unitId);
    java.util.Optional<OrganizationUnitEntity> findByUnitId(String unitId);
}
