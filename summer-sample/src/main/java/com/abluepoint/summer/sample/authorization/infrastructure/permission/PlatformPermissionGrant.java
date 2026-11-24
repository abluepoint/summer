package com.abluepoint.summer.sample.authorization.infrastructure.permission;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/** Tenant ownership of a role stays independent of the platform-owned permission definition. */
@Entity
@Table(name = "summer_role_platform_permission")
@IdClass(PlatformPermissionGrant.Key.class)
class PlatformPermissionGrant {
  @Id @Column(name = "domain_key", length = 96) private String domainKey;
  @Id @Column(name = "role_id", length = 96) private String roleId;
  @Id @Column(name = "permission_id", length = 96) private String permissionId;
  @Column(name = "catalog_domain_key", nullable = false, length = 96)
  private String catalogDomainKey;

  protected PlatformPermissionGrant() {}

  public static class Key implements Serializable {
    public String domainKey;
    public String roleId;
    public String permissionId;
    @Override public boolean equals(Object other) {
      return other instanceof Key k && Objects.equals(domainKey, k.domainKey)
          && Objects.equals(roleId, k.roleId) && Objects.equals(permissionId, k.permissionId);
    }
    @Override public int hashCode() { return Objects.hash(domainKey, roleId, permissionId); }
  }
}
