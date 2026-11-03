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
    private final MenuManifestService service;
    private final RequestIdAccessor requestId;

    MenuManifestController(MenuManifestService service, RequestIdAccessor requestId) { this.service = service; this.requestId = requestId; }

    @GetMapping("/v1/platform/menu-manifest")
    @RawResponse
    @ResourcePermission("platform.menu.write")
    ResponseEntity<ApiResponse<MenuManifestView>> current(Authentication authentication) {
         MenuManifestView view = preserveAccessDenied(() -> service.current(authentication)); return result(HttpStatus.OK, view, view.manifestVersion());
    }

    @PutMapping(value = "/v1/platform/menu-manifest", consumes = "application/json")
    @RawResponse
    @ResourcePermission("platform.menu.write")
    ResponseEntity<ApiResponse<MenuManifestView>> sync(@RequestBody MenuManifestSyncRequest body,
                                                        @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                        Authentication authentication) {
         MenuManifestView view = preserveAccessDenied(() -> service.sync(body, key, authentication)); return result(HttpStatus.OK, view, view.manifestVersion());
    }

    @GetMapping("/v1/enterprises/{enterpriseId}/menu-manifest")
    @RawResponse
    ResponseEntity<ApiResponse<MenuManifestView>> visible(@PathVariable String enterpriseId, Authentication authentication) {
         MenuManifestView view = preserveAccessDenied(() -> service.visible(enterpriseId, authentication)); return result(HttpStatus.OK, view, view.manifestVersion());
    }

    @GetMapping("/v1/enterprises/{enterpriseId}/menu-manifest/management")
    @RawResponse
    @ResourcePermission("enterprise.menu.write")
    ResponseEntity<ApiResponse<MenuManifestView>> management(@PathVariable String enterpriseId, Authentication authentication) {
         MenuManifestView view = preserveAccessDenied(() -> service.management(enterpriseId, authentication)); return result(HttpStatus.OK, view, view.manifestVersion());
    }

    @PatchMapping(value = "/v1/enterprises/{enterpriseId}/menu-manifest/{menuId}", consumes = "application/json")
    @RawResponse
    @ResourcePermission("enterprise.menu.write")
    ResponseEntity<ApiResponse<MenuManifestItemView>> override(@PathVariable String enterpriseId, @PathVariable String menuId,
                                                                 @RequestBody MenuManifestOverrideRequest body,
                                                                 @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                                 @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                                 Authentication authentication) {
         MenuManifestItemView view = preserveAccessDenied(() -> service.override(enterpriseId, menuId, body, ifMatch, key, authentication)); return result(HttpStatus.OK, view, view.version());
    }

    // Nested frontend route IDs contain '/', which must not be encoded into a
    // path segment or require weakening the HTTP firewall's encoded-slash rule.
    @PatchMapping(value = "/v1/enterprises/{enterpriseId}/menu-manifest", consumes = "application/json")
    @RawResponse
    @ResourcePermission("enterprise.menu.write")
    ResponseEntity<ApiResponse<MenuManifestItemView>> overrideById(@PathVariable String enterpriseId,
                                                                  @RequestParam String menuId,
                                                                  @RequestBody MenuManifestOverrideRequest body,
                                                                  @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                                  @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                                  Authentication authentication) {
        return override(enterpriseId, menuId, body, ifMatch, key, authentication);
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
