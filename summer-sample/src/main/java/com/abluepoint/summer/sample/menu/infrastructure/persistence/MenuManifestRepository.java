package com.abluepoint.summer.sample.menu.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface MenuManifestRepository extends JpaRepository<MenuManifestEntity, String> {
    public List<MenuManifestEntity> findAllByOrderBySortOrderAscMenuIdAsc();
    // The manifest row exists before any tenant override, including its first write.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MenuManifestEntity m where m.menuId = :id")
    public Optional<MenuManifestEntity> lockMenu(@Param("id") String id);
}
