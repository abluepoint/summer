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
  private final com.abluepoint.summer.sample.authorization.application.permission.InterfaceOperationService operations;
  private final com.abluepoint.summer.mvc.request.RequestIdAccessor requestIds;
  private final com.abluepoint.summer.sample.authorization.application.permission.MenuNavigationService navigation;
  private final com.abluepoint.summer.sample.authorization.application.permission
          .ManagedPermissionAccess
      access;

  public PermissionAccessController(
      ManagedRoleService roles,
      com.abluepoint.summer.sample.authorization.application.permission.InterfaceOperationService operations,
      com.abluepoint.summer.mvc.request.RequestIdAccessor requestIds,
      com.abluepoint.summer.sample.authorization.application.permission.MenuNavigationService navigation,
      com.abluepoint.summer.sample.authorization.application.permission.ManagedPermissionAccess
          access) {
    this.roles = roles;
    this.operations = operations;
    this.requestIds = requestIds;
    this.navigation = navigation;
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
    return ApiResponse.success(navigation.visible(PermissionController.domain(r), a), null);
  }

  @GetMapping("/operations")
  public Object operations(HttpServletRequest request, Authentication actor, @RequestParam java.util.List<String> actions) {
    requireIdentity(request, actor);
    return ApiResponse.success(Map.of("actions", operations.qualify(PermissionController.domain(request), actor, actions,
        requestIds.currentRequestId())), requestIds.currentRequestId());
  }

  private void requireIdentity(HttpServletRequest r, Authentication a) {
    if (!access.active(PermissionController.domain(r), a))
      throw new org.springframework.security.access.AccessDeniedException(
          "Active domain identity required");
  }
}
