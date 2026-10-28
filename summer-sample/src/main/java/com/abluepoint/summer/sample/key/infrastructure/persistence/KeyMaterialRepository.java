package com.abluepoint.summer.sample.key.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KeyMaterialRepository extends JpaRepository<KeyMaterialEntity, String> {
}
