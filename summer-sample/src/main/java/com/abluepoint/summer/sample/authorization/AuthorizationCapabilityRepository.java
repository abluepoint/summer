package com.abluepoint.summer.sample.authorization;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

interface AuthorizationCapabilityRepository extends JpaRepository<AuthorizationCapabilityEntity, String> {
    List<AuthorizationCapabilityEntity> findAllByOrderByCapabilityIdAsc();
}
