package com.abluepoint.summer.sample.organization.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnitEntity, Long> {
    boolean existsByUnitId(String unitId);
    java.util.Optional<OrganizationUnitEntity> findByUnitId(String unitId);
}
