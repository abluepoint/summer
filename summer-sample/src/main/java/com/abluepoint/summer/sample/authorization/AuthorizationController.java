package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import org.springframework.security.access.AccessDeniedException;
import java.util.function.Supplier;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.resource.ResourcePermission;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@PreAuthorize("isAuthenticated()")
class AuthorizationController {
    private final AuthorizationService service; private final RequestIdAccessor requestId;
    AuthorizationController(AuthorizationService s, RequestIdAccessor r) { service = s; requestId = r; }

    @ResourcePermission("enterprise.capabilities.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/capabilities/catalog") @RawResponse
    ResponseEntity<ApiResponse<CapabilityCatalogView>> capabilities(@PathVariable String enterpriseId, Authentication a, HttpServletRequest req) {  CapabilityCatalogView v = preserveAccessDenied(() -> service.capabilities(enterpriseId, a)); return read(v, req);  }
    @ResourcePermission("platform.capabilities.read")
    @GetMapping("/api/v1/platform/capabilities/catalog") @RawResponse
    ResponseEntity<ApiResponse<CapabilityCatalogView>> platformCapabilities(Authentication a, HttpServletRequest req) {  return read(preserveAccessDenied(() -> service.platformCapabilities(a)), req);  }
    @ResourcePermission("platform.capabilities.write")
    @PostMapping(value = "/api/v1/platform/capabilities", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse
    ResponseEntity<ApiResponse<CapabilityView>> createPlatformCapability(@RequestBody CapabilityCreateRequest body, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) {  CapabilityView v = preserveAccessDenied(() -> service.createPlatformCapability(body, key, rid(), a)); return write(HttpStatus.CREATED, v, v.version());  }
    @ResourcePermission("enterprise.capabilities.write")
    @PostMapping(value = "/api/v1/enterprises/{enterpriseId}/capabilities", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse
    ResponseEntity<ApiResponse<CapabilityView>> createCapability(@PathVariable String enterpriseId, @RequestBody CapabilityCreateRequest body, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) {  CapabilityView v = preserveAccessDenied(() -> service.createCapability(enterpriseId, body, key, rid(), a)); return write(HttpStatus.CREATED, v, v.version());  }
    @ResourcePermission("enterprise.capabilities.write")
    @PostMapping("/api/v1/enterprises/{enterpriseId}/capabilities/{id}:disable") @RawResponse
    ResponseEntity<ApiResponse<CapabilityView>> disable(@PathVariable String enterpriseId, @PathVariable String id, @RequestHeader("If-Match") String etag, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) { return status(enterpriseId, id, "DISABLED", etag, key, a); }
    @ResourcePermission("enterprise.capabilities.write")
    @PostMapping("/api/v1/enterprises/{enterpriseId}/capabilities/{id}:enable") @RawResponse
    ResponseEntity<ApiResponse<CapabilityView>> enable(@PathVariable String enterpriseId, @PathVariable String id, @RequestHeader("If-Match") String etag, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) { return status(enterpriseId, id, "ACTIVE", etag, key, a); }
    @ResourcePermission("enterprise.capabilities.write")
    @PatchMapping(value = "/api/v1/enterprises/{enterpriseId}/capabilities/{id}", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse
    ResponseEntity<ApiResponse<CapabilityView>> updateCapability(@PathVariable String enterpriseId, @PathVariable String id, @RequestBody CapabilityStatusRequest body, @RequestHeader("If-Match") String etag, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) { return status(enterpriseId, id, body == null ? null : body.status(), etag, key, a); }
    private ResponseEntity<ApiResponse<CapabilityView>> status(String e, String id, String status, String etag, String key, Authentication a) {  CapabilityView v = preserveAccessDenied(() -> service.setCapabilityStatus(e, id, status, etag, key, rid(), a)); return write(HttpStatus.OK, v, v.version());  }

    @ResourcePermission("enterprise.roles.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/roles") @RawResponse ResponseEntity<ApiResponse<List<RoleView>>> roles(@PathVariable String enterpriseId, Authentication a) {  return write(HttpStatus.OK, preserveAccessDenied(() -> service.roles(enterpriseId, a)), null);  }
    @ResourcePermission("enterprise.roles.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/roles/{id}") @RawResponse ResponseEntity<ApiResponse<RoleView>> role(@PathVariable String enterpriseId, @PathVariable String id, Authentication a) {  RoleView v = preserveAccessDenied(() -> service.role(enterpriseId, id, a)); return write(HttpStatus.OK, v, v.version());  }
    @ResourcePermission("enterprise.roles.write")
    @PostMapping(value = "/api/v1/enterprises/{enterpriseId}/roles", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<RoleView>> createRole(@PathVariable String enterpriseId, @RequestBody RoleCreateRequest body, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) {  RoleView v = preserveAccessDenied(() -> service.createRole(enterpriseId, body, key, rid(), a)); return write(HttpStatus.CREATED, v, v.version());  }
    @ResourcePermission("enterprise.roles.write")
    @PatchMapping(value = "/api/v1/enterprises/{enterpriseId}/roles/{id}", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<RoleView>> updateRole(@PathVariable String enterpriseId, @PathVariable String id, @RequestBody RoleUpdateRequest body, @RequestHeader("If-Match") String etag, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) {  RoleView v = preserveAccessDenied(() -> service.updateRole(enterpriseId, id, body, etag, key, rid(), a)); return write(HttpStatus.OK, v, v.version());  }
    @ResourcePermission("enterprise.roles.write")
    @PutMapping(value = "/api/v1/enterprises/{enterpriseId}/roles/{id}/capabilities", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<RoleView>> roleCapabilities(@PathVariable String enterpriseId, @PathVariable String id, @RequestBody RoleCapabilitiesRequest body, @RequestHeader("If-Match") String etag, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) {  RoleView v = preserveAccessDenied(() -> service.setRoleCapabilities(enterpriseId, id, body, etag, key, rid(), a)); return write(HttpStatus.OK, v, v.version());  }
    @ResourcePermission("enterprise.roles.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/roles/{id}/capabilities") @RawResponse ResponseEntity<ApiResponse<List<String>>> roleCapabilityIds(@PathVariable String enterpriseId, @PathVariable String id, Authentication a) {  return write(HttpStatus.OK, preserveAccessDenied(() -> service.role(enterpriseId, id, a)).capabilityIds(), null);  }

    @ResourcePermission("enterprise.bundles.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/capability-bundles") @RawResponse ResponseEntity<ApiResponse<List<BundleView>>> bundles(@PathVariable String enterpriseId, Authentication a) {  return write(HttpStatus.OK, preserveAccessDenied(() -> service.bundles(enterpriseId, a)), null);  }
    @ResourcePermission("enterprise.bundles.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/capability-bundles/{id}") @RawResponse ResponseEntity<ApiResponse<BundleView>> bundle(@PathVariable String enterpriseId, @PathVariable String id, Authentication a) {  BundleView v = preserveAccessDenied(() -> service.bundle(enterpriseId, id, a)); return write(HttpStatus.OK, v, v.version());  }
    @ResourcePermission("enterprise.bundles.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/capability-bundles/{id}/versions") @RawResponse ResponseEntity<ApiResponse<List<BundleVersionView>>> bundleVersions(@PathVariable String enterpriseId, @PathVariable String id, Authentication a) {  return write(HttpStatus.OK, preserveAccessDenied(() -> service.bundleVersions(enterpriseId, id, a)), null);  }
    @ResourcePermission("enterprise.bundles.write")
    @PostMapping(value = "/api/v1/enterprises/{enterpriseId}/capability-bundles", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<BundleView>> createBundle(@PathVariable String enterpriseId, @RequestBody BundleCreateRequest body, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) {  BundleView v = preserveAccessDenied(() -> service.createBundle(enterpriseId, body, key, rid(), a)); return write(HttpStatus.CREATED, v, v.version());  }
    @ResourcePermission("enterprise.bundles.write")
    @PatchMapping(value = "/api/v1/enterprises/{enterpriseId}/capability-bundles/{id}", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<BundleView>> updateBundle(@PathVariable String enterpriseId, @PathVariable String id, @RequestBody BundleUpdateRequest body, @RequestHeader("If-Match") String etag, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) {  BundleView v = preserveAccessDenied(() -> service.updateBundle(enterpriseId, id, body, etag, key, rid(), a)); return write(HttpStatus.OK, v, v.version());  }
    @ResourcePermission("enterprise.bundles.write")
    @PutMapping(value = "/api/v1/enterprises/{enterpriseId}/capability-bundles/{id}/capabilities", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<BundleView>> updateBundleCapabilities(@PathVariable String enterpriseId, @PathVariable String id, @RequestBody BundleUpdateRequest body, @RequestHeader("If-Match") String etag, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) {  BundleView v = preserveAccessDenied(() -> service.updateBundle(enterpriseId, id, body, etag, key, rid(), a)); return write(HttpStatus.OK, v, v.version());  }
    @ResourcePermission("enterprise.roles.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/authorization-audit/{id}") @RawResponse ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> audit(@PathVariable String enterpriseId, @PathVariable String id, Authentication a) {  return write(HttpStatus.OK, preserveAccessDenied(() -> service.audit(enterpriseId, id, a)), null);  }

    @ResourcePermission("enterprise.roles.write")
    @PutMapping(value = "/api/v1/enterprises/{enterpriseId}/users/{username}/roles", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RawResponse
    ResponseEntity<ApiResponse<UserRoleAssignmentView>> assignUserRoles(
            @PathVariable String enterpriseId,
            @PathVariable String username,
            @RequestBody UserRoleAssignmentRequest body,
            @RequestHeader(value = "If-Match", required = false) String etag,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            Authentication authentication) {
        UserRoleAssignmentView view = preserveAccessDenied(() -> service.assignUserRoles(enterpriseId, username, body, etag, key, rid(), authentication));
        return write(HttpStatus.OK, view, view.version());

    }

    @ResourcePermission("enterprise.roles.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/users/{username}/roles") @RawResponse
    ResponseEntity<ApiResponse<UserRoleAssignmentView>> userRoles(@PathVariable String enterpriseId, @PathVariable String username, Authentication authentication) {
         UserRoleAssignmentView view = preserveAccessDenied(() -> service.userAssignment(enterpriseId, username, authentication)); return write(HttpStatus.OK, view, view.version());
    }

    private String rid() { return requestId.currentRequestId(); }
    private <T> ResponseEntity<ApiResponse<T>> read(T body, HttpServletRequest req) { String etag = "\"" + (body instanceof CapabilityCatalogView c ? c.version() : body.hashCode()) + "\""; if (etag.equals(req.getHeader(HttpHeaders.IF_NONE_MATCH))) return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).header("X-Request-Id", rid()).build(); return write(HttpStatus.OK, body, Long.parseLong(etag.substring(1, etag.length() - 1))); }
    private <T> ResponseEntity<ApiResponse<T>> write(HttpStatus status, T body, Long version) { ResponseEntity.BodyBuilder b = ResponseEntity.status(status).header("X-Request-Id", rid()); if (version != null) b.eTag("\"" + version + "\""); return b.body(ApiResponse.success(body, rid())); }

    /** Preserve this application's original security message without changing the native service contract. */
    private <T> T preserveAccessDenied(Supplier<T> action) {
        try {
            return action.get();
        } catch (AccessDeniedException denied) {
            throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, denied.getMessage(), denied);
        }
    }
}
