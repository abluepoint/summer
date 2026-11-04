package com.abluepoint.summer.sample.authorization.infrastructure.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/** Persistent domain ownership bridge shared with legacy role compatibility. */
@Entity
@Table(name = "summer_permission_role")
@IdClass(PermissionRoleOwnership.Key.class)
class PermissionRoleOwnership {
  @Id
  @Column(name = "domain_key", nullable = false, length = 96)
  private String domainKey;

  @Id
  @Column(name = "role_id", nullable = false, length = 96)
  private String roleId;

  protected PermissionRoleOwnership() {}

  public static class Key implements Serializable {
    public String domainKey;
    public String roleId;

    public Key() {}

    @Override
    public boolean equals(Object other) {
      return other instanceof Key key
          && Objects.equals(domainKey, key.domainKey)
          && Objects.equals(roleId, key.roleId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(domainKey, roleId);
    }
  }
}
