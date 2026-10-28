package com.abluepoint.summer.sample.plugin;

import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.common.error.api.CommonErrorCode;
import com.abluepoint.summer.common.error.api.ErrorCode;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.error.spi.ErrorStatusResolver;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import com.abluepoint.summer.sample.resource.ResourcePermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
class PluginController {
    private final PluginService service;
    private final RequestIdAccessor requestId;
    private final ErrorStatusResolver errorStatus;

    PluginController(PluginService service, RequestIdAccessor requestId, ErrorStatusResolver errorStatus) {
        this.service = service;
        this.requestId = requestId;
        this.errorStatus = errorStatus;
    }

    @GetMapping("/v1/platform/plugins")
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_platform.plugins.read')")
    ResponseEntity<ApiResponse<PluginPage>> list(Authentication authentication) { return ok(service.platformList(authentication)); }

    @PostMapping(value = "/v1/platform/plugins", consumes = "application/json")
    @RawResponse
    @PreAuthorize("hasAuthority('SCOPE_platform.plugins.write')")
    ResponseEntity<ApiResponse<PluginView>> register(@RequestBody PluginRegisterRequest body, Authentication authentication) {
        try { return body(HttpStatus.CREATED, service.register(body, authentication)); } catch (Exception e) { return error(e); }
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

    private <T> ResponseEntity<ApiResponse<T>> lifecycle(ThrowingSupplier<T> call) { try { return ok(call.get()); } catch (Exception e) { return error(e); } }
    private <T> ResponseEntity<ApiResponse<T>> ok(T value) { return body(HttpStatus.OK, value); }
    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus status, T value) { String rid = requestId.currentRequestId(); return ResponseEntity.status(status).header("X-Request-Id", rid).body(ApiResponse.success(value, rid)); }
    private <T> ResponseEntity<ApiResponse<T>> error(Exception exception) {
        ErrorCode code = CommonErrorCode.INTERNAL_ERROR;
        if (exception instanceof AccessDeniedException) code = CommonErrorCode.FORBIDDEN;
        else if (exception instanceof PluginValidationException) code = PluginErrorCode.INVALID;
        else if (exception instanceof PluginTrustException) code = PluginErrorCode.UNTRUSTED;
        else if (exception instanceof PluginCompatibilityException) code = PluginErrorCode.INCOMPATIBLE;
        else if (exception instanceof PluginNotFoundException) code = CommonErrorCode.NOT_FOUND;
        else if (exception instanceof RuntimeException failure) throw failure;
        String rid = requestId.currentRequestId();
        return ResponseEntity.status(errorStatus.resolve(code)).header("X-Request-Id", rid)
                .body(ApiResponse.failure(new ApiError(code.code(), exception.getMessage(), List.of()), rid));
    }
    @FunctionalInterface private interface ThrowingSupplier<T> { T get() throws Exception; }
}
