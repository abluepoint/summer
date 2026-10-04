package com.abluepoint.summer.sample.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "summer_menu_manifest_override")
class MenuManifestOverrideEntity {
    @Id @Column(name = "override_id", length = 240, nullable = false) private String overrideId;
    @Column(name = "enterprise_id", length = 64, nullable = false) private String enterpriseId;
    @Column(name = "menu_id", length = 160, nullable = false) private String menuId;
    @Column(name = "enabled") private Boolean enabled;
    @Column(name = "parent_menu_id", length = 160) private String parentMenuId;
    @Column(name = "sort_order") private Integer sortOrder;
    @Column(name = "capability_code", length = 200) private String capabilityCode;
    @Column(name = "version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected MenuManifestOverrideEntity() {}
    MenuManifestOverrideEntity(String enterpriseId, String menuId, MenuManifestOverrideRequest request) {
        this.overrideId = enterpriseId + ":" + menuId; this.enterpriseId = enterpriseId; this.menuId = menuId;
        update(request, 1); this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
    void update(MenuManifestOverrideRequest request, long nextVersion) {
        if (request.enabled() != null) enabled = request.enabled();
        if (request.parentMenuId() != null) parentMenuId = request.parentMenuId();
        if (request.sortOrder() != null) sortOrder = request.sortOrder();
        if (request.capabilityCode() != null) capabilityCode = request.capabilityCode();
        version = nextVersion; updatedAt = Instant.now();
    }
    String overrideId() { return overrideId; } String enterpriseId() { return enterpriseId; } String menuId() { return menuId; }
    Boolean enabled() { return enabled; } String parentMenuId() { return parentMenuId; } Integer sortOrder() { return sortOrder; }
    String capabilityCode() { return capabilityCode; } long version() { return version; }
}
