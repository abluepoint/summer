package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.mvc.annotation.Field;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.authorization.application.permission.ManagedRoleService;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionManagementGuard;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RawResponse
public class PlatformRoleDeletionController {
  private final ManagedRoleService roles;
  private final PermissionManagementGuard guard;
  private final RequestIdAccessor requests;

  public PlatformRoleDeletionController(ManagedRoleService roles, PermissionManagementGuard guard,
      RequestIdAccessor requests) {
    this.roles = roles;
    this.guard = guard;
    this.requests = requests;
  }

  @Field(param = "id", style = "requestTextStyle")
  @DeleteMapping("/api/v1/platform/roles/{id}")
  public ResponseEntity<?> delete(@PathVariable String id, Authentication actor,
      @RequestHeader(value = "If-Match", required = false) String condition,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return deleteRole(Domain.platform(), id, actor, condition, key);
  }

  @Field(param = "enterpriseId", style = "requestTextStyle")
  @Field(param = "id", style = "requestTextStyle")
  @DeleteMapping("/api/v1/enterprises/{enterpriseId}/roles/{id}")
  public ResponseEntity<?> deleteTenantRole(@PathVariable String enterpriseId, @PathVariable String id,
      Authentication actor, @RequestHeader(value = "If-Match", required = false) String condition,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return deleteRole(Domain.enterprise(enterpriseId), id, actor, condition, key);
  }

  private ResponseEntity<?> deleteRole(Domain domain, String id, Authentication actor,
      String condition, String key) {
    guard.require(domain, actor, true);
    String requestId = requests.currentRequestId();
    var result = roles.mutate(domain, actor.getName(), "role", id, "DELETE", Map.of(), condition,
        key, requestId);
    return ResponseEntity.status(result.status()).eTag("\"" + result.version() + "\"")
        .body(ApiResponse.success(result.body(), requestId));
  }
}
