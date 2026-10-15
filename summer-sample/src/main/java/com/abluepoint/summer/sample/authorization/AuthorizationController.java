package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.common.response.ApiError;
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
    ResponseEntity<ApiResponse<CapabilityCatalogView>> capabilities(@PathVariable String enterpriseId, Authentication a, HttpServletRequest req) { try { CapabilityCatalogView v = service.capabilities(enterpriseId, a); return read(v, req); } catch (Exception e) { return error(e); } }
    @ResourcePermission("platform.capabilities.read")
    @GetMapping("/api/v1/platform/capabilities/catalog") @RawResponse
    ResponseEntity<ApiResponse<CapabilityCatalogView>> platformCapabilities(Authentication a, HttpServletRequest req) { try { return read(service.platformCapabilities(a), req); } catch (Exception e) { return error(e); } }
    @ResourcePermission("platform.capabilities.write")
    @PostMapping(value = "/api/v1/platform/capabilities", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse
    ResponseEntity<ApiResponse<CapabilityView>> createPlatformCapability(@RequestBody CapabilityCreateRequest body, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) { try { CapabilityView v = service.createPlatformCapability(body, key, rid(), a); return write(HttpStatus.CREATED, v, v.version()); } catch (Exception e) { return error(e); } }
    @ResourcePermission("enterprise.capabilities.write")
    @PostMapping(value = "/api/v1/enterprises/{enterpriseId}/capabilities", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse
    ResponseEntity<ApiResponse<CapabilityView>> createCapability(@PathVariable String enterpriseId, @RequestBody CapabilityCreateRequest body, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) { try { CapabilityView v = service.createCapability(enterpriseId, body, key, rid(), a); return write(HttpStatus.CREATED, v, v.version()); } catch (Exception e) { return error(e); } }
    @ResourcePermission("enterprise.capabilities.write")
    @PostMapping("/api/v1/enterprises/{enterpriseId}/capabilities/{id}:disable") @RawResponse
    ResponseEntity<ApiResponse<CapabilityView>> disable(@PathVariable String enterpriseId, @PathVariable String id, @RequestHeader("If-Match") String etag, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) { return status(enterpriseId, id, "DISABLED", etag, key, a); }
    @ResourcePermission("enterprise.capabilities.write")
    @PostMapping("/api/v1/enterprises/{enterpriseId}/capabilities/{id}:enable") @RawResponse
    ResponseEntity<ApiResponse<CapabilityView>> enable(@PathVariable String enterpriseId, @PathVariable String id, @RequestHeader("If-Match") String etag, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) { return status(enterpriseId, id, "ACTIVE", etag, key, a); }
    @ResourcePermission("enterprise.capabilities.write")
    @PatchMapping(value = "/api/v1/enterprises/{enterpriseId}/capabilities/{id}", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse
    ResponseEntity<ApiResponse<CapabilityView>> updateCapability(@PathVariable String enterpriseId, @PathVariable String id, @RequestBody CapabilityStatusRequest body, @RequestHeader("If-Match") String etag, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) { return status(enterpriseId, id, body == null ? null : body.status(), etag, key, a); }
    private ResponseEntity<ApiResponse<CapabilityView>> status(String e, String id, String status, String etag, String key, Authentication a) { try { CapabilityView v = service.setCapabilityStatus(e, id, status, etag, key, rid(), a); return write(HttpStatus.OK, v, v.version()); } catch (Exception x) { return error(x); } }

    @ResourcePermission("enterprise.roles.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/roles") @RawResponse ResponseEntity<ApiResponse<List<RoleView>>> roles(@PathVariable String enterpriseId, Authentication a) { try { return write(HttpStatus.OK, service.roles(enterpriseId, a), null); } catch (Exception e) { return error(e); } }
    @ResourcePermission("enterprise.roles.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/roles/{id}") @RawResponse ResponseEntity<ApiResponse<RoleView>> role(@PathVariable String enterpriseId, @PathVariable String id, Authentication a) { try { RoleView v = service.role(enterpriseId, id, a); return write(HttpStatus.OK, v, v.version()); } catch (Exception e) { return error(e); } }
    @ResourcePermission("enterprise.roles.write")
    @PostMapping(value = "/api/v1/enterprises/{enterpriseId}/roles", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<RoleView>> createRole(@PathVariable String enterpriseId, @RequestBody RoleCreateRequest body, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) { try { RoleView v = service.createRole(enterpriseId, body, key, rid(), a); return write(HttpStatus.CREATED, v, v.version()); } catch (Exception e) { return error(e); } }
    @ResourcePermission("enterprise.roles.write")
    @PatchMapping(value = "/api/v1/enterprises/{enterpriseId}/roles/{id}", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<RoleView>> updateRole(@PathVariable String enterpriseId, @PathVariable String id, @RequestBody RoleUpdateRequest body, @RequestHeader("If-Match") String etag, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) { try { RoleView v = service.updateRole(enterpriseId, id, body, etag, key, rid(), a); return write(HttpStatus.OK, v, v.version()); } catch (Exception e) { return error(e); } }
    @ResourcePermission("enterprise.roles.write")
    @PutMapping(value = "/api/v1/enterprises/{enterpriseId}/roles/{id}/capabilities", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<RoleView>> roleCapabilities(@PathVariable String enterpriseId, @PathVariable String id, @RequestBody RoleCapabilitiesRequest body, @RequestHeader("If-Match") String etag, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) { try { RoleView v = service.setRoleCapabilities(enterpriseId, id, body, etag, key, rid(), a); return write(HttpStatus.OK, v, v.version()); } catch (Exception e) { return error(e); } }
    @ResourcePermission("enterprise.roles.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/roles/{id}/capabilities") @RawResponse ResponseEntity<ApiResponse<List<String>>> roleCapabilityIds(@PathVariable String enterpriseId, @PathVariable String id, Authentication a) { try { return write(HttpStatus.OK, service.role(enterpriseId, id, a).capabilityIds(), null); } catch (Exception e) { return error(e); } }

    @ResourcePermission("enterprise.bundles.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/capability-bundles") @RawResponse ResponseEntity<ApiResponse<List<BundleView>>> bundles(@PathVariable String enterpriseId, Authentication a) { try { return write(HttpStatus.OK, service.bundles(enterpriseId, a), null); } catch (Exception e) { return error(e); } }
    @ResourcePermission("enterprise.bundles.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/capability-bundles/{id}") @RawResponse ResponseEntity<ApiResponse<BundleView>> bundle(@PathVariable String enterpriseId, @PathVariable String id, Authentication a) { try { BundleView v = service.bundle(enterpriseId, id, a); return write(HttpStatus.OK, v, v.version()); } catch (Exception e) { return error(e); } }
    @ResourcePermission("enterprise.bundles.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/capability-bundles/{id}/versions") @RawResponse ResponseEntity<ApiResponse<List<BundleVersionView>>> bundleVersions(@PathVariable String enterpriseId, @PathVariable String id, Authentication a) { try { return write(HttpStatus.OK, service.bundleVersions(enterpriseId, id, a), null); } catch (Exception e) { return error(e); } }
    @ResourcePermission("enterprise.bundles.write")
    @PostMapping(value = "/api/v1/enterprises/{enterpriseId}/capability-bundles", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<BundleView>> createBundle(@PathVariable String enterpriseId, @RequestBody BundleCreateRequest body, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) { try { BundleView v = service.createBundle(enterpriseId, body, key, rid(), a); return write(HttpStatus.CREATED, v, v.version()); } catch (Exception e) { return error(e); } }
    @ResourcePermission("enterprise.bundles.write")
    @PatchMapping(value = "/api/v1/enterprises/{enterpriseId}/capability-bundles/{id}", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<BundleView>> updateBundle(@PathVariable String enterpriseId, @PathVariable String id, @RequestBody BundleUpdateRequest body, @RequestHeader("If-Match") String etag, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) { try { BundleView v = service.updateBundle(enterpriseId, id, body, etag, key, rid(), a); return write(HttpStatus.OK, v, v.version()); } catch (Exception e) { return error(e); } }
    @ResourcePermission("enterprise.bundles.write")
    @PutMapping(value = "/api/v1/enterprises/{enterpriseId}/capability-bundles/{id}/capabilities", consumes = MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<BundleView>> updateBundleCapabilities(@PathVariable String enterpriseId, @PathVariable String id, @RequestBody BundleUpdateRequest body, @RequestHeader("If-Match") String etag, @RequestHeader(value = "Idempotency-Key", required = false) String key, Authentication a) { try { BundleView v = service.updateBundle(enterpriseId, id, body, etag, key, rid(), a); return write(HttpStatus.OK, v, v.version()); } catch (Exception e) { return error(e); } }
    @ResourcePermission("enterprise.roles.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/authorization-audit/{id}") @RawResponse ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> audit(@PathVariable String enterpriseId, @PathVariable String id, Authentication a) { try { return write(HttpStatus.OK, service.audit(enterpriseId, id, a), null); } catch (Exception e) { return error(e); } }

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
        try {
            UserRoleAssignmentView view = service.assignUserRoles(enterpriseId, username, body, etag, key, rid(), authentication);
            return write(HttpStatus.OK, view, view.version());
        } catch (Exception exception) {
            return error(exception);
        }
    }

    @ResourcePermission("enterprise.roles.read")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/users/{username}/roles") @RawResponse
    ResponseEntity<ApiResponse<UserRoleAssignmentView>> userRoles(@PathVariable String enterpriseId, @PathVariable String username, Authentication authentication) {
        try { UserRoleAssignmentView view = service.userAssignment(enterpriseId, username, authentication); return write(HttpStatus.OK, view, view.version()); }
        catch (Exception exception) { return error(exception); }
    }

    private String rid() { return requestId.currentRequestId(); }
    private <T> ResponseEntity<ApiResponse<T>> read(T body, HttpServletRequest req) { String etag = "\"" + (body instanceof CapabilityCatalogView c ? c.version() : body.hashCode()) + "\""; if (etag.equals(req.getHeader(HttpHeaders.IF_NONE_MATCH))) return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).header("X-Request-Id", rid()).build(); return write(HttpStatus.OK, body, Long.parseLong(etag.substring(1, etag.length() - 1))); }
    private <T> ResponseEntity<ApiResponse<T>> write(HttpStatus status, T body, Long version) { ResponseEntity.BodyBuilder b = ResponseEntity.status(status).header("X-Request-Id", rid()); if (version != null) b.eTag("\"" + version + "\""); return b.body(ApiResponse.success(body, rid())); }
    private <T> ResponseEntity<ApiResponse<T>> error(Exception e) { HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR; String code = "SUMMER_INTERNAL_ERROR"; if (e instanceof org.springframework.security.access.AccessDeniedException) { status = HttpStatus.FORBIDDEN; code = "SUMMER_FORBIDDEN"; } else if (e instanceof AuthorizationValidationException) { status = HttpStatus.BAD_REQUEST; code = "SUMMER_AUTHORIZATION_INVALID"; } else if (e instanceof AuthorizationPreconditionException) { status = HttpStatus.PRECONDITION_FAILED; code = "SUMMER_PRECONDITION_FAILED"; } else if (e instanceof AuthorizationIdempotencyConflictException) { status = HttpStatus.CONFLICT; code = "SUMMER_IDEMPOTENCY_CONFLICT"; } else if (e instanceof AuthorizationConflictException) { status = HttpStatus.CONFLICT; code = "SUMMER_AUTHORIZATION_CONFLICT"; } return ResponseEntity.status(status).header("X-Request-Id", rid()).body(ApiResponse.failure(new ApiError(code, e.getMessage(), List.of()), rid())); }
}
