package com.abluepoint.summer.sample.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "summer_menu_manifest_item")
class MenuManifestEntity {
    @Id @Column(name = "menu_id", length = 160, nullable = false) private String menuId;
    @Column(name = "parent_menu_id", length = 160) private String parentMenuId;
    @Column(name = "path", length = 500, nullable = false) private String path;
    @Column(name = "title_key", length = 240, nullable = false) private String titleKey;
    @Column(name = "icon_key", length = 120, nullable = false) private String iconKey;
    @Column(name = "client_code", nullable = false, length = 80, columnDefinition = "varchar(80) default 'web'") private String clientCode;
    String clientCode() { return clientCode; }
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "capability_code", length = 200) private String capabilityCode;
    @Column(name = "manifest_version", nullable = false) private long manifestVersion;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected MenuManifestEntity() {}
    MenuManifestEntity(MenuManifestItemRequest item, long version) {
        this.clientCode = item.clientCode(); this.menuId = item.menuId(); this.parentMenuId = item.parentMenuId(); this.path = item.path();
        this.titleKey = item.titleKey(); this.iconKey = item.iconKey(); this.sortOrder = item.sortOrder();
        this.capabilityCode = item.capabilityCode(); this.manifestVersion = version;
        this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }
    void update(MenuManifestItemRequest item, long version) {
        clientCode = item.clientCode(); parentMenuId = item.parentMenuId(); path = item.path(); titleKey = item.titleKey(); iconKey = item.iconKey();
        sortOrder = item.sortOrder(); capabilityCode = item.capabilityCode(); manifestVersion = version; updatedAt = Instant.now();
    }
    String menuId() { return menuId; } String parentMenuId() { return parentMenuId; } String path() { return path; }
    String titleKey() { return titleKey; } String iconKey() { return iconKey; } int sortOrder() { return sortOrder; }
    String capabilityCode() { return capabilityCode; } long manifestVersion() { return manifestVersion; }
}
