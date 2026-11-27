package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.mvc.annotation.Field;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionManagementGuard;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionResourceBindingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RawResponse
@RequestMapping("/api/v1/platform")
public class InterfacePermissionController {
  private final PermissionResourceBindingService bindings;
  private final PermissionManagementGuard guard;
  private final RequestIdAccessor requestId;
  private final com.abluepoint.summer.sample.authorization.infrastructure.permission
          .ManagedInterfaceResources
      resources;

  public InterfacePermissionController(
      PermissionResourceBindingService bindings,
      PermissionManagementGuard guard,
      RequestIdAccessor requestId,
      com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources
          resources) {
    this.bindings = bindings;
    this.guard = guard;
    this.requestId = requestId;
    this.resources = resources;
  }

  @Field(param = "page", required = false, style = "requestIntegerParameterStyle")
  @Field(param = "size", required = false, style = "requestIntegerParameterStyle")
  @GetMapping("/authorization/interface-directory")
  public Object directory(
      HttpServletRequest request,
      Authentication actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    var domain = PermissionController.domain(request);
    guard.require(domain, actor, false);
    return ApiResponse.success(
        resources.directory(domain, page, size), requestId.currentRequestId());
  }

  @Field(param = "permissionId", style = "requestTextStyle")
  @GetMapping("/permissions/{permissionId}/interfaces")
  public Object read(
      HttpServletRequest request, Authentication actor, @PathVariable String permissionId) {
    var domain = PermissionController.domain(request);
    guard.require(domain, actor, false);
    var result = bindings.readCatalog(domain, permissionId, "INTERFACE");
    return ResponseEntity.ok()
        .eTag("\"" + result.version() + "\"")
        .body(ApiResponse.success(result.body(), requestId.currentRequestId()));
  }

  @PutMapping("/permissions/{permissionId}/interfaces")
  public Object replace(
      HttpServletRequest request,
      Authentication actor,
      @PathVariable String permissionId,
      @RequestBody java.util.Map<String, Object> body,
      @RequestHeader(value = "If-Match", required = false) String match,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var domain = PermissionController.domain(request);
    guard.requireDefinitionWrite(domain, actor);
    if (!body.keySet().equals(java.util.Set.of("resourceIds"))
        || !(body.get("resourceIds") instanceof java.util.List<?> ids)
        || ids.stream().anyMatch(value -> !(value instanceof String)))
      throw com.abluepoint.summer.common.error.SummerException.withMessage(
          com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode.INVALID,
          "Only an explicit resourceIds string list is accepted");
    var result =
        bindings.replace(
            domain,
            actor.getName(),
            permissionId,
            "INTERFACE",
            ids.stream().map(String.class::cast).toList(),
            match,
            key,
            requestId.currentRequestId());
    return ResponseEntity.status(result.status())
        .eTag("\"" + result.version() + "\"")
        .body(ApiResponse.success(result.body(), requestId.currentRequestId()));
  }
}
