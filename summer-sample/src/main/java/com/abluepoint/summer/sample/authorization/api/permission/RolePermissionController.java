package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.authorization.AuthorizationService;
import com.abluepoint.summer.sample.authorization.application.permission.*;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RawResponse
@RequestMapping({"/api/v1/enterprises/{enterpriseId}", "/api/v1/platform"})
public class RolePermissionController {
  private final ManagedRoleService roles;
  private final ManagedPermissionAccess access;
  private final PermissionManagementGuard guard;
  private final AuthorizationService legacy;
  private final RequestIdAccessor rid;

  public RolePermissionController(
      ManagedRoleService roles,
      ManagedPermissionAccess access,
      PermissionManagementGuard guard,
      AuthorizationService legacy,
      RequestIdAccessor rid) {
    this.roles = roles;
    this.access = access;
    this.guard = guard;
    this.legacy = legacy;
    this.rid = rid;
  }

  @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.roles.read")
  @GetMapping({
    "/roles",
    "/roles/{id}",
    "/roles/{id}/permissions",
    "/roles/{id}/capabilities",
    "/users/{username}/roles"
  })
  public Object read(
      HttpServletRequest request,
      Authentication auth,
      @PathVariable Map<String, String> variables) {
    return execute(
        request,
        auth,
        variables.getOrDefault("id", variables.get("username")),
        Map.of(),
        null,
        null);
  }

  @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.roles.write")
  @PostMapping("/roles")
  public Object create(
      HttpServletRequest r,
      Authentication a,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return execute(r, a, null, body, null, key);
  }

  @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.roles.write")
  @PatchMapping("/roles/{id}")
  public Object patch(
      HttpServletRequest r,
      Authentication a,
      @PathVariable String id,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "If-Match", required = false) String condition,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return execute(r, a, id, body, condition, key);
  }

  @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.roles.write")
  @PutMapping({"/roles/{id}/permissions", "/roles/{id}/capabilities", "/users/{username}/roles"})
  public Object replace(
      HttpServletRequest r,
      Authentication a,
      @PathVariable Map<String, String> variables,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "If-Match", required = false) String condition,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return execute(
        r, a, variables.getOrDefault("id", variables.get("username")), body, condition, key);
  }

  private Object execute(
      HttpServletRequest r,
      Authentication a,
      String id,
      Map<String, Object> body,
      String condition,
      String key) {
    Domain d = PermissionController.domain(r);
    boolean read = r.getMethod().equals("GET");
    Object template =
        r.getAttribute(
            org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    String path = template.toString();
    String target =
        path.endsWith("/users/{username}/roles")
            ? "user"
            : path.endsWith("/permissions") && id != null
                ? "permissions"
                : path.endsWith("/capabilities") ? "capabilities" : "role";
    if (!access.managed(d)) {
      if (d.kind().equals("PLATFORM") || target.equals("permissions")) {
        guard.require(d, a, !read);
        throw new org.springframework.security.access.AccessDeniedException(
            "Managed domain required");
      }
      Object result;
      try {
        result =
            legacy.legacyRoleRequest(
                d.id(), a, r.getMethod(), target, id, body, condition, key, rid.currentRequestId());
      } catch (org.springframework.security.access.AccessDeniedException denied) {
        throw com.abluepoint.summer.common.error.SummerException.withMessage(
            com.abluepoint.summer.common.error.CommonErrorCode.FORBIDDEN,
            denied.getMessage(),
            denied);
      }
      var response = ResponseEntity.status(r.getMethod().equals("POST") ? 201 : 200);
      Long version = legacy.legacyRoleVersion(result);
      if (version != null) response.eTag("\"" + version + "\"");
      return response.body(ApiResponse.success(result, rid.currentRequestId()));
    }
    guard.require(d, a, !read);
    if (target.equals("capabilities"))
      throw com.abluepoint.summer.common.error.SummerException.withMessage(
          com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode.INVALID,
          "Legacy capability fields are unsupported in managed mode");
    if (read) {
      Object value =
          target.equals("user")
              ? roles.user(d, id)
              : id == null ? roles.list(d, query(r)) : roles.role(d, id);
      var response = ResponseEntity.ok();
      if (value instanceof Map<?, ?> v && v.get("version") instanceof Number version)
        response.eTag("\"" + version + "\"");
      return response.body(ApiResponse.success(value, rid.currentRequestId()));
    }
    var result =
        roles.mutate(
            d,
            a.getName(),
            target,
            id,
            r.getMethod(),
            body,
            condition,
            key,
            rid.currentRequestId());
    return ResponseEntity.status(result.status())
        .eTag("\"" + result.version() + "\"")
        .body(ApiResponse.success(result.body(), rid.currentRequestId()));
  }

  private static Map<String, String> query(HttpServletRequest request) {
    Map<String, String> values = new LinkedHashMap<>();
    request
        .getParameterMap()
        .forEach(
            (key, v) -> {
              if (v.length != 1)
                throw com.abluepoint.summer.common.error.SummerException.withMessage(
                    com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode
                        .INVALID,
                    "Repeated role query parameter");
              values.put(key, v[0]);
            });
    return values;
  }
}
