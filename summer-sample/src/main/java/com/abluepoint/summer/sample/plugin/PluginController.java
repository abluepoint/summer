package com.abluepoint.summer.sample.plugin;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.resource.ResourcePermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.function.Supplier;

@RestController
@RequestMapping("/api")
class PluginController {
    private final PluginService service;
    private final RequestIdAccessor requestId;

    PluginController(PluginService service, RequestIdAccessor requestId) {
        this.service = service;
        this.requestId = requestId;
    }

    @GetMapping("/v1/platform/plugins")
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_platform.plugins.read')")
    ResponseEntity<ApiResponse<PluginPage>> list(Authentication authentication) { return ok(service.platformList(authentication)); }

    @PostMapping(value = "/v1/platform/plugins", consumes = "application/json")
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_platform.plugins.write')")
    ResponseEntity<ApiResponse<PluginView>> register(@RequestBody PluginRegisterRequest body, Authentication authentication) {
        return body(HttpStatus.CREATED, nativeBoundary(() -> service.register(body, authentication)));
    }

    @PostMapping("/v1/platform/plugins/{pluginId}:enable")
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_platform.plugins.write')")
    ResponseEntity<ApiResponse<PluginView>> enable(@PathVariable String pluginId, Authentication authentication) { return lifecycle(() -> service.enable(pluginId, authentication)); }

    @PostMapping("/v1/platform/plugins/{pluginId}:disable")
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_platform.plugins.write')")
    ResponseEntity<ApiResponse<PluginView>> disable(@PathVariable String pluginId, Authentication authentication) { return lifecycle(() -> service.disable(pluginId, authentication)); }

    @PostMapping(value = "/v1/platform/plugins/{pluginId}:upgrade", consumes = "application/json")
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_platform.plugins.write')")
    ResponseEntity<ApiResponse<PluginView>> upgrade(@PathVariable String pluginId, @RequestBody PluginUpgradeRequest body, Authentication authentication) { return lifecycle(() -> service.upgrade(pluginId, body, authentication)); }

    @GetMapping("/v1/platform/plugins/{pluginId}/diagnostic")
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_platform.plugins.read')")
    ResponseEntity<ApiResponse<PluginDiagnosticView>> diagnostic(@PathVariable String pluginId, Authentication authentication) { return lifecycle(() -> service.diagnostic(pluginId, authentication)); }

    @GetMapping("/v1/enterprises/{enterpriseId}/plugins")
    @RawResponse
    @ResourcePermission("enterprise.plugins.read")
    ResponseEntity<ApiResponse<PluginPage>> enterpriseList(@PathVariable String enterpriseId, Authentication authentication) { return lifecycle(() -> service.enterpriseList(enterpriseId, authentication)); }

    @GetMapping("/v1/enterprises/{enterpriseId}/plugins/customer-insights/summary")
    @RawResponse
    @ResourcePermission("enterprise.plugins.customer-insights.read")
    ResponseEntity<ApiResponse<PluginInsightsView>> customerInsights(@PathVariable String enterpriseId, Authentication authentication) { return lifecycle(() -> service.customerInsights(enterpriseId, authentication)); }

    private <T> ResponseEntity<ApiResponse<T>> lifecycle(Supplier<T> call) { return ok(nativeBoundary(call)); }
    private <T> ResponseEntity<ApiResponse<T>> ok(T value) { return body(HttpStatus.OK, value); }
    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus status, T value) { String rid = requestId.currentRequestId(); return ResponseEntity.status(status).header("X-Request-Id", rid).body(ApiResponse.success(value, rid)); }
    // Preserve the message contract of this existing native permission boundary.
    private <T> T nativeBoundary(Supplier<T> call) {
        try { return call.get(); }
        catch (AccessDeniedException denied) {
            throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, denied.getMessage(), denied);
        }
    }
}
