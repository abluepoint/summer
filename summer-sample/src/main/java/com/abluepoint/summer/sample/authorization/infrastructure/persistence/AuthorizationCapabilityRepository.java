package com.abluepoint.summer.sample.authorization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuthorizationCapabilityRepository extends JpaRepository<AuthorizationCapabilityEntity, String> {
    public List<AuthorizationCapabilityEntity> findAllByOrderByCapabilityIdAsc();
}
