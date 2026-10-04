package com.abluepoint.summer.sample.organization;

import org.springframework.data.jpa.repository.JpaRepository;

interface OrganizationContextRepository extends JpaRepository<OrganizationContextEntity, Long> {
    java.util.Optional<OrganizationContextEntity> findBySubject(String subject);
}
