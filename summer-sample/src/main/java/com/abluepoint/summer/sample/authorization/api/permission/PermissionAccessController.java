package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.sample.authorization.application.permission.ManagedRoleService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Small real resource surface for explaining the caller's current role access. */
@RestController
@RequestMapping({
  "/api/v1/enterprises/{enterpriseId}/authorization",
  "/api/v1/platform/authorization"
})
public class PermissionAccessController {
  private final ManagedRoleService roles;
  private final com.abluepoint.summer.sample.authorization.application.permission
          .ManagedPermissionAccess
      access;

  public PermissionAccessController(
      ManagedRoleService roles,
      com.abluepoint.summer.sample.authorization.application.permission.ManagedPermissionAccess
          access) {
    this.roles = roles;
    this.access = access;
  }

  @GetMapping("/status")
  public Object status() {
    return ApiResponse.success(Map.of("available", true), null);
  }

  @GetMapping("/profile")
  public Object profile(HttpServletRequest r, Authentication a) {
    requireIdentity(r, a);
    return ApiResponse.success(Map.of("subject", a.getName()), null);
  }

  @GetMapping("/access")
  public Object access(HttpServletRequest r, Authentication a) {
    requireIdentity(r, a);
    return ApiResponse.success(roles.user(PermissionController.domain(r), a.getName()), null);
  }

  @GetMapping("/navigation")
  public Object navigation(HttpServletRequest r, Authentication a) {
    var d = PermissionController.domain(r);
    if (!access.managed(d))
      throw new org.springframework.security.access.AccessDeniedException(
          "Managed domain required");
    requireIdentity(r, a);
    var items =
        java.util.List.of("roles", "permissions").stream()
            .filter(menuId -> access.allows(d, a, "MENU", menuId))
            .map(
                id ->
                    Map.<String, Object>of(
                        "menuId",
                        id,
                        "path",
                        "/" + id,
                        "titleKey",
                        id.equals("roles") ? "角色与权限" : "权限管理",
                        "iconKey",
                        id,
                        "sortOrder",
                        0,
                        "enabled",
                        true,
                        "actions",
                        java.util.List.of("read"),
                        "version",
                        1))
            .toList();
    return ApiResponse.success(Map.of("manifestVersion", 1, "items", items), null);
  }

  private void requireIdentity(HttpServletRequest r, Authentication a) {
    if (!access.active(PermissionController.domain(r), a))
      throw new org.springframework.security.access.AccessDeniedException(
          "Active domain identity required");
  }
}
