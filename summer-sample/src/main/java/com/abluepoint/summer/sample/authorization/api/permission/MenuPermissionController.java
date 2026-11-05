package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionManagementGuard;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionResourceBindingService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RawResponse
@RequestMapping({"/api/v1/platform", "/api/v1/enterprises/{enterpriseId}"})
public class MenuPermissionController {
  private final PermissionResourceBindingService bindings;
  private final PermissionManagementGuard guard;
  private final RequestIdAccessor rid;

  public MenuPermissionController(
      PermissionResourceBindingService bindings,
      PermissionManagementGuard guard,
      RequestIdAccessor rid) {
    this.bindings = bindings;
    this.guard = guard;
    this.rid = rid;
  }

  @GetMapping("/permissions/{permissionId}/menus")
  public Object read(HttpServletRequest r, Authentication a, @PathVariable String permissionId) {
    var domain = PermissionController.domain(r);
    guard.require(domain, a, false);
    var result = bindings.read(domain, permissionId, "MENU");
    return ResponseEntity.status(result.status())
        .eTag("\"" + result.version() + "\"")
        .body(ApiResponse.success(result.body(), rid.currentRequestId()));
  }

  @PutMapping("/permissions/{permissionId}/menus")
  public Object replace(
      HttpServletRequest r,
      Authentication a,
      @PathVariable String permissionId,
      @RequestBody Map<String, Object> body,
      @RequestHeader(value = "If-Match", required = false) String match,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var domain = PermissionController.domain(r);
    guard.require(domain, a, true);
    if (!body.keySet().equals(Set.of("menuIds"))
        || !(body.get("menuIds") instanceof List<?> values)
        || values.stream().anyMatch(value -> !(value instanceof String)))
      throw com.abluepoint.summer.common.error.SummerException.withMessage(
          com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode.INVALID,
          "Only a menuIds string array is accepted");
    var result =
        bindings.replace(
            domain,
            a.getName(),
            permissionId,
            "MENU",
            values.stream().map(String.class::cast).toList(),
            match,
            key,
            rid.currentRequestId());
    return ResponseEntity.status(result.status())
        .eTag("\"" + result.version() + "\"")
        .body(ApiResponse.success(result.body(), rid.currentRequestId()));
  }
}
