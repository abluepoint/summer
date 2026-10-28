package com.abluepoint.summer.sample.menu.infrastructure.persistence;

import com.abluepoint.summer.sample.menu.domain.MenuManifestItem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "summer_menu_manifest_item")
public class MenuManifestEntity {
    @Id @Column(name = "menu_id", length = 160, nullable = false) private String menuId;
    @Column(name = "parent_menu_id", length = 160) private String parentMenuId;
    @Column(name = "path", length = 500, nullable = false) private String path;
    @Column(name = "title_key", length = 240, nullable = false) private String titleKey;
    @Column(name = "icon_key", length = 120, nullable = false) private String iconKey;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "capability_code", length = 200) private String capabilityCode;
    @Column(name = "manifest_version", nullable = false) private long manifestVersion;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected MenuManifestEntity() {}
    public MenuManifestEntity(MenuManifestItem item, long version) {
        this.menuId = item.menuId(); this.parentMenuId = item.parentMenuId(); this.path = item.path();
        this.titleKey = item.titleKey(); this.iconKey = item.iconKey(); this.sortOrder = item.sortOrder();
        this.capabilityCode = item.capabilityCode(); this.manifestVersion = version;
        this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
    public void update(MenuManifestItem item, long version) {
        parentMenuId = item.parentMenuId(); path = item.path(); titleKey = item.titleKey(); iconKey = item.iconKey();
        sortOrder = item.sortOrder(); capabilityCode = item.capabilityCode(); manifestVersion = version; updatedAt = Instant.now();
    }
    public String menuId() { return menuId; } public String parentMenuId() { return parentMenuId; } public String path() { return path; }
    public String titleKey() { return titleKey; } public String iconKey() { return iconKey; } public int sortOrder() { return sortOrder; }
    public String capabilityCode() { return capabilityCode; } public long manifestVersion() { return manifestVersion; }
}
