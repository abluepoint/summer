package com.abluepoint.summer.sample.menu.infrastructure.persistence;

import com.abluepoint.summer.sample.menu.application.command.MenuManifestOverrideCommand;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "summer_menu_manifest_override")
public class MenuManifestOverrideEntity {
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
    public MenuManifestOverrideEntity(String enterpriseId, String menuId, MenuManifestOverrideCommand request) {
        this.overrideId = enterpriseId + ":" + menuId; this.enterpriseId = enterpriseId; this.menuId = menuId;
        update(request, 2); this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
    public void update(MenuManifestOverrideCommand request, long nextVersion) {
        if (request.enabled() != null) enabled = request.enabled();
        if (request.parentMenuId() != null) parentMenuId = request.parentMenuId();
        if (request.sortOrder() != null) sortOrder = request.sortOrder();
        if (request.capabilityCode() != null) capabilityCode = request.capabilityCode();
        version = nextVersion; updatedAt = Instant.now();
    }
    public String overrideId() { return overrideId; } public String enterpriseId() { return enterpriseId; } public String menuId() { return menuId; }
    public Boolean enabled() { return enabled; } public String parentMenuId() { return parentMenuId; } public Integer sortOrder() { return sortOrder; }
    public String capabilityCode() { return capabilityCode; } public long version() { return version; }
}
