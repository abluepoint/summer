package com.abluepoint.summer.sample.systemparameter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SystemParameterRepository extends JpaRepository<SystemParameterEntity, String> {
    Optional<SystemParameterEntity> findByKey(String key);
    boolean existsByKey(String key);
    List<SystemParameterEntity> findAllByTenantIdOrderByKeyAsc(String tenantId);
}
