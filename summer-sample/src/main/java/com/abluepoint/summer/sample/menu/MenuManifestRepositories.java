package com.abluepoint.summer.sample.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

interface MenuManifestRepository extends JpaRepository<MenuManifestEntity, String> {
    List<MenuManifestEntity> findAllByOrderBySortOrderAscMenuIdAsc();
}
interface MenuManifestOverrideRepository extends JpaRepository<MenuManifestOverrideEntity, String> {
    List<MenuManifestOverrideEntity> findAllByEnterpriseId(String enterpriseId);
    Optional<MenuManifestOverrideEntity> findByEnterpriseIdAndMenuId(String enterpriseId, String menuId);
}
