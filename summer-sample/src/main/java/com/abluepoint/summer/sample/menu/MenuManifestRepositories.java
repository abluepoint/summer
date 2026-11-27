package com.abluepoint.summer.sample.menu;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface MenuManifestRepository extends JpaRepository<MenuManifestEntity, String> {
    List<MenuManifestEntity> findAllByOrderBySortOrderAscMenuIdAsc();
}
