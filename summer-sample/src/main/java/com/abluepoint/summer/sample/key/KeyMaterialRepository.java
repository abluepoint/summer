package com.abluepoint.summer.sample.key;

import org.springframework.data.jpa.repository.JpaRepository;

interface KeyMaterialRepository extends JpaRepository<KeyMaterialEntity, String> {
}
