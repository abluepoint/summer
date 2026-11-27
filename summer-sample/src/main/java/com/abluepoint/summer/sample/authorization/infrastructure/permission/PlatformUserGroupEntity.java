package com.abluepoint.summer.sample.authorization.infrastructure.permission;

import jakarta.persistence.*;
import java.util.Set;

/** Flyway owns the production schema. These mappings also support Hibernate-managed profiles. */
@Entity
@Table(name = "summer_platform_user_group")
class PlatformUserGroupEntity {
  @Id @Column(length = 64) String id;
  @Column(nullable = false, unique = true, length = 200) String name;
  @Column(nullable = false, length = 500) String description;

  @ElementCollection
  @CollectionTable(name = "summer_platform_group_role", joinColumns = @JoinColumn(name = "group_id"))
  @Column(name = "role_id", nullable = false, length = 96)
  Set<String> roles;

  @ElementCollection
  @CollectionTable(name = "summer_platform_group_administrator", joinColumns = @JoinColumn(name = "group_id"))
  @Column(name = "username", nullable = false, length = 128)
  Set<String> administrators;
}
