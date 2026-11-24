package com.abluepoint.summer.sample.authorization.application.permission;

/** Both sources preserve the role's domain; historical grants never become global grants. */
public final class RolePermissionGrants {
  private RolePermissionGrants() {}
  public static final String RELATION =
      "(select domain_key,role_id,permission_id,domain_key as permission_domain_key"
          + " from summer_role_permission union all select domain_key,role_id,permission_id,"
          + " catalog_domain_key as permission_domain_key from summer_role_platform_permission)";
}
