package com.abluepoint.summer.sample.menu.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MenuManifestOverrideRepository extends JpaRepository<MenuManifestOverrideEntity, String> {
    public List<MenuManifestOverrideEntity> findAllByEnterpriseId(String enterpriseId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    public Optional<MenuManifestOverrideEntity> findByEnterpriseIdAndMenuId(String enterpriseId, String menuId);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MenuManifestOverrideEntity m set m.enabled = coalesce(:enabled, m.enabled), " +
            "m.parentMenuId = coalesce(:parent, m.parentMenuId), m.sortOrder = coalesce(:sortOrder, m.sortOrder), " +
            "m.capabilityCode = coalesce(:capability, m.capabilityCode), m.version = m.version + 1, m.updatedAt = :now " +
            "where m.overrideId = :id and m.version = :expected")
    public int compareAndSet(@Param("id") String id, @Param("expected") long expected,
                      @Param("enabled") Boolean enabled, @Param("parent") String parent,
                      @Param("sortOrder") Integer sortOrder, @Param("capability") String capability,
                      @Param("now") Instant now);
}
