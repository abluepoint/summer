package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.authorization.application.permission.MenuCatalogService;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionManagementGuard;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RawResponse
@RequestMapping("/api/v1/platform")
public class MenuDirectoryController {
  private final MenuCatalogService menus;
  private final PermissionManagementGuard guard;
  private final RequestIdAccessor rid;

  public MenuDirectoryController(
      MenuCatalogService menus, PermissionManagementGuard guard, RequestIdAccessor rid) {
    this.menus = menus;
    this.guard = guard;
    this.rid = rid;
  }

  @GetMapping({"/authorization/menu-directory", "/authorization/menu-layout"})
  public Object directory(HttpServletRequest r, Authentication a) {
    var domain = PermissionController.domain(r);
    guard.require(domain, a, false);
    if (r.getRequestURI().endsWith("/menu-layout")) requirePlatform(domain);
    var body = available(() -> menus.read(domain, false));
    return ResponseEntity.ok()
        .eTag("\"" + body.get("manifestVersion") + "\"")
        .body(ApiResponse.success(body, rid.currentRequestId()));
  }

  @GetMapping("/authorization/menu-manifest")
  public Object manifest(HttpServletRequest r, Authentication a) {
    var domain = PermissionController.domain(r);
    requirePlatform(domain);
    guard.require(domain, a, false);
    var body = available(() -> menus.read(domain, true));
    return ResponseEntity.ok()
        .eTag("\"" + body.get("manifestVersion") + "\"")
        .body(ApiResponse.success(body, rid.currentRequestId()));
  }

  @PutMapping("/authorization/menu-manifest")
  public Object publish(
      HttpServletRequest r,
      Authentication a,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "If-Match", required = false) String match,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var domain = PermissionController.domain(r);
    guard.require(domain, a, true);
    return result(
        available(
            () ->
                menus.mutate(domain, a.getName(), null, body, match, key, rid.currentRequestId())));
  }

  @PatchMapping("/authorization/menu-layout")
  public Object layout(
      HttpServletRequest r,
      Authentication a,
      @RequestParam String menuId,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "If-Match", required = false) String match,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var domain = PermissionController.domain(r);
    guard.require(domain, a, true);
    return result(
        available(
            () ->
                menus.mutate(
                    domain, a.getName(), menuId, body, match, key, rid.currentRequestId())));
  }

  private static void requirePlatform(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain domain) {
    if (!domain.equals(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform()))
      throw new org.springframework.security.access.AccessDeniedException("Menus are managed by the platform");
  }

  private <T> T available(java.util.function.Supplier<T> operation) {
    try {
      return operation.get();
    } catch (org.springframework.dao.DataAccessException
        | org.springframework.transaction.TransactionException failure) {
      throw com.abluepoint.summer.common.error.SummerException.withMessage(
          com.abluepoint.summer.sample.error.SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE,
          "Current menu metadata unavailable");
    }
  }

  private Object result(PermissionService.Result result) {
    return ResponseEntity.status(result.status())
        .eTag("\"" + result.version() + "\"")
        .body(ApiResponse.success(result.body(), rid.currentRequestId()));
  }
}
