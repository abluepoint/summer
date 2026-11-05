package com.abluepoint.summer.sample.authorization.infrastructure.permission;

import jakarta.persistence.*;

@Entity
@Table(name = "summer_menu_publication")
public class MenuPublicationEntity {
  @Id
  @Column(name = "publication_id", length = 32)
  private String id;

  @Column(name = "published", nullable = false)
  private boolean published;

  @Column(name = "manifest_version", nullable = false)
  private long version;

  protected MenuPublicationEntity() {}
}
