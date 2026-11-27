package com.abluepoint.summer.sample.authorization.infrastructure.permission;

import jakarta.persistence.*;

/** Schema mapping for Hibernate-managed local profiles. Flyway owns production DDL. */
@Entity
@Table(name = "summer_platform_administrator")
class PlatformAdministratorEntity {
  @Id
  @Column(length = 128)
  String username;

  @Column(name = "display_name", nullable = false, length = 200)
  String displayName;
}
