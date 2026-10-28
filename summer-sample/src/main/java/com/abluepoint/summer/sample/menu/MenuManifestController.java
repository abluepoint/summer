package com.abluepoint.summer.sample.menu;

import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import com.abluepoint.summer.sample.resource.api.ResourcePermission;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        try { MenuManifestView view = service.current(authentication); return result(HttpStatus.OK, view, view.manifestVersion()); }
        catch (Exception e) { return error(e); }
    }

    @PutMapping(value = "/v1/platform/menu-manifest", consumes = "application/json")
    @RawResponse
    @ResourcePermission("platform.menu.write")
    ResponseEntity<ApiResponse<MenuManifestView>> sync(@RequestBody MenuManifestSyncRequest body,
                                                        @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                        Authentication authentication) {
        try { MenuManifestView view = service.sync(body, key, authentication); return result(HttpStatus.OK, view, view.manifestVersion()); }
        catch (Exception e) { return error(e); }
    }

    @GetMapping("/v1/enterprises/{enterpriseId}/menu-manifest")
    @RawResponse
    ResponseEntity<ApiResponse<MenuManifestView>> visible(@PathVariable String enterpriseId, Authentication authentication) {
        try { MenuManifestView view = service.visible(enterpriseId, authentication); return result(HttpStatus.OK, view, view.manifestVersion()); }
        catch (Exception e) { return error(e); }
    }

    @GetMapping("/v1/enterprises/{enterpriseId}/menu-manifest/management")
    @RawResponse
    @ResourcePermission("enterprise.menu.write")
    ResponseEntity<ApiResponse<MenuManifestView>> management(@PathVariable String enterpriseId, Authentication authentication) {
        try { MenuManifestView view = service.management(enterpriseId, authentication); return result(HttpStatus.OK, view, view.manifestVersion()); }
        catch (Exception e) { return error(e); }
    }

    @PatchMapping(value = "/v1/enterprises/{enterpriseId}/menu-manifest/{menuId}", consumes = "application/json")
    @RawResponse
    @ResourcePermission("enterprise.menu.write")
    ResponseEntity<ApiResponse<MenuManifestItemView>> override(@PathVariable String enterpriseId, @PathVariable String menuId,
                                                                 @RequestBody MenuManifestOverrideRequest body,
                                                                 @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                                 @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                                 Authentication authentication) {
        try { MenuManifestItemView view = service.override(enterpriseId, menuId, body, ifMatch, key, authentication); return result(HttpStatus.OK, view, view.version()); }
        catch (Exception e) { return error(e); }
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
    private <T> ResponseEntity<ApiResponse<T>> error(Exception exception) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR; String code = "SUMMER_INTERNAL_ERROR";
        if (exception instanceof AccessDeniedException) { status = HttpStatus.FORBIDDEN; code = "SUMMER_FORBIDDEN"; }
        else if (exception instanceof MenuManifestValidationException) { status = HttpStatus.BAD_REQUEST; code = "SUMMER_MENU_MANIFEST_INVALID"; }
        else if (exception instanceof MenuManifestPreconditionException) { status = HttpStatus.PRECONDITION_FAILED; code = "SUMMER_PRECONDITION_FAILED"; }
        else if (exception instanceof MenuManifestConflictException) { status = HttpStatus.CONFLICT; code = "SUMMER_MENU_MANIFEST_CONFLICT"; }
        return ResponseEntity.status(status).header("X-Request-Id", rid()).body(ApiResponse.failure(new ApiError(code, exception.getMessage(), List.of()), rid()));
    }
}
