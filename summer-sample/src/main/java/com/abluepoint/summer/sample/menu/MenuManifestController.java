package com.abluepoint.summer.sample.menu;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import org.springframework.security.access.AccessDeniedException;
import java.util.function.Supplier;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.resource.ResourcePermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api")
final class MenuManifestController {
    private final com.abluepoint.summer.sample.resource.ManagedMenuOperations managed;
    private final MenuManifestService service;
    private final RequestIdAccessor requestId;

    MenuManifestController(MenuManifestService service, RequestIdAccessor requestId) { this(service, requestId, null); }

    @org.springframework.beans.factory.annotation.Autowired
    MenuManifestController(MenuManifestService service, RequestIdAccessor requestId, com.abluepoint.summer.sample.resource.ManagedMenuOperations managed) {
        this.managed = managed; this.service = service; this.requestId = requestId; }

    @GetMapping("/v1/platform/menu-manifest")
    @RawResponse
    @ResourcePermission("platform.menu.write")
    ResponseEntity<?> current(Authentication authentication) {
        if (managed != null && managed.managed(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform())) {
            var view = managed.read(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), authentication, true);
            return result(HttpStatus.OK, view, ((Number)view.get("manifestVersion")).longValue());
        }
         MenuManifestView view = preserveAccessDenied(() -> service.current(authentication)); return result(HttpStatus.OK, view, view.manifestVersion());
    }

    @PutMapping(value = "/v1/platform/menu-manifest", consumes = "application/json")
    @RawResponse
    @ResourcePermission("platform.menu.write")
    ResponseEntity<?> sync(@RequestBody MenuManifestSyncRequest body,
                                                        @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                        Authentication authentication,
                                                        @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        if (managed != null && managed.managed(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform())) {
            if (body == null || body.items() == null || body.items().stream().anyMatch(item -> item.capabilityCode() != null))
                throw SummerException.withMessage(MenuManifestErrorCode.INVALID, "Only menu metadata is accepted in managed mode");
            java.util.List<java.util.Map<String,Object>> items = body.items().stream().map(item -> {
                java.util.Map<String,Object> row = new java.util.LinkedHashMap<>();
                row.put("menuId",item.menuId()); row.put("parentMenuId",item.parentMenuId()); row.put("path",item.path());
                row.put("titleKey",item.titleKey()); row.put("iconKey",item.iconKey()); row.put("sortOrder",item.sortOrder());
                return row;
            }).toList();
            return managedResult(managed.mutate(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), authentication, null,
                java.util.Map.of("manifestVersion",body.manifestVersion(),"items",items),ifMatch,key,rid()));
        }
         MenuManifestView view = preserveAccessDenied(() -> service.sync(body, key, authentication)); return result(HttpStatus.OK, view, view.manifestVersion());
    }

    @GetMapping("/v1/enterprises/{enterpriseId}/menu-manifest")
    @RawResponse
    ResponseEntity<?> visible(@PathVariable String enterpriseId, Authentication authentication) {
        var domain = com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise(enterpriseId);
        if (managed != null && managed.managed(domain)) {
            var view=managed.visible(domain,authentication);
            return result(HttpStatus.OK,view,((Number)view.get("manifestVersion")).longValue());
        }
         MenuManifestView view = preserveAccessDenied(() -> service.visible(enterpriseId, authentication)); return result(HttpStatus.OK, view, view.manifestVersion());
    }

    @GetMapping("/v1/enterprises/{enterpriseId}/menu-manifest/management")
    @RawResponse
    @ResourcePermission("enterprise.menu.write")
    ResponseEntity<?> management(@PathVariable String enterpriseId, Authentication authentication) {
        var domain = com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise(enterpriseId);
        if (managed != null && managed.managed(domain)) {
            var view=managed.read(domain,authentication,false);
            return result(HttpStatus.OK,view,((Number)view.get("manifestVersion")).longValue());
        }
         MenuManifestView view = preserveAccessDenied(() -> service.management(enterpriseId, authentication)); return result(HttpStatus.OK, view, view.manifestVersion());
    }

    @PatchMapping(value = "/v1/enterprises/{enterpriseId}/menu-manifest/{menuId}", consumes = "application/json")
    @RawResponse
    @ResourcePermission("enterprise.menu.write")
    ResponseEntity<?> override(@PathVariable String enterpriseId, @PathVariable String menuId,
                                                                 @RequestBody MenuManifestOverrideRequest body,
                                                                 @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                                 @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                                 Authentication authentication) {
        var domain = com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise(enterpriseId);
        if (managed != null && managed.managed(domain)) {
            if (body == null || body.capabilityCode() != null)
                throw SummerException.withMessage(MenuManifestErrorCode.INVALID, "Only menu metadata is accepted in managed mode");
            java.util.Map<String,Object> metadata = new java.util.LinkedHashMap<>();
            if (body.enabled() != null) metadata.put("enabled",body.enabled());
            if (body.parentMenuId() != null) metadata.put("parentMenuId",body.parentMenuId());
            if (body.sortOrder() != null) metadata.put("sortOrder",body.sortOrder());
            return managedResult(managed.mutate(domain,authentication,menuId,metadata,ifMatch,key,rid()));
        }
         MenuManifestItemView view = preserveAccessDenied(() -> service.override(enterpriseId, menuId, body, ifMatch, key, authentication)); return result(HttpStatus.OK, view, view.version());
    }

    // Nested frontend route IDs contain '/', which must not be encoded into a
    // path segment or require weakening the HTTP firewall's encoded-slash rule.
    @PatchMapping(value = "/v1/enterprises/{enterpriseId}/menu-manifest", consumes = "application/json")
    @RawResponse
    @ResourcePermission("enterprise.menu.write")
    ResponseEntity<?> overrideById(@PathVariable String enterpriseId,
                                                                  @RequestParam String menuId,
                                                                  @RequestBody MenuManifestOverrideRequest body,
                                                                  @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                                  @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                                  Authentication authentication) {
        return override(enterpriseId, menuId, body, ifMatch, key, authentication);
    }

    private ResponseEntity<?> managedResult(com.abluepoint.summer.sample.resource.ManagedMenuOperations.Mutation changed) {
        return result(HttpStatus.valueOf(changed.status()),changed.body(),changed.version());
    }

    private String rid() { return requestId.currentRequestId(); }
    private <T> ResponseEntity<ApiResponse<T>> result(HttpStatus status, T value, long version) {
        return ResponseEntity.status(status).eTag("\"" + version + "\"").header("X-Request-Id", rid()).body(ApiResponse.success(value, rid()));
    }

    /** Preserve this application's original security message without changing the native service contract. */
    private <T> T preserveAccessDenied(Supplier<T> action) {
        try {
            return action.get();
        } catch (AccessDeniedException denied) {
            throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, denied.getMessage(), denied);
        }
    }
}
