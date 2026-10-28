package com.abluepoint.summer.sample.organization.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationContextRepository extends JpaRepository<OrganizationContextEntity, Long> {
    java.util.Optional<OrganizationContextEntity> findBySubject(String subject);
}
