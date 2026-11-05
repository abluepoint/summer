package com.abluepoint.summer.sample.authorization.infrastructure.permission;

import jakarta.persistence.*;

/** Explicit domain identity keeps enterprise platform separate from the platform domain. */
@Entity
@Table(
    name = "summer_menu_domain_layout",
    uniqueConstraints = @UniqueConstraint(columnNames = {"domain_key", "menu_id"}))
public class MenuDomainLayoutEntity {
  @Id
  @Column(name = "layout_id", length = 320)
  private String id;

  @Column(name = "domain_key", length = 100, nullable = false)
  private String domainKey;

  @Column(name = "menu_id", length = 160, nullable = false)
  private String menuId;

  @Column(name = "enabled")
  private Boolean enabled;

  @Column(name = "parent_menu_id", length = 160)
  private String parentMenuId;

  @Column(name = "sort_order")
  private Integer sortOrder;

  @Column(name = "version", nullable = false)
  private long version;

  protected MenuDomainLayoutEntity() {}
}
