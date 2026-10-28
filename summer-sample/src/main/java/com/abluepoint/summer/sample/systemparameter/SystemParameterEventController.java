package com.abluepoint.summer.sample.systemparameter;

import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/system-parameter-events", "/api/v1/enterprises/{enterpriseId}/system-parameter-events"})
class SystemParameterEventController {
    private final SystemParameterEventService service;
    private final RequestIdAccessor requestId;

    SystemParameterEventController(SystemParameterEventService service, RequestIdAccessor requestId) {
        this.service = service;
        this.requestId = requestId;
    }

    @GetMapping
    @com.abluepoint.summer.sample.resource.ResourcePermission("system.parameter.read")
    @RawResponse
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.read','SCOPE_platform.parameter.read','SCOPE_customer.read')")
    ResponseEntity<ApiResponse<Map<String, Object>>> list(@RequestParam(required = false) String key, org.springframework.security.core.Authentication auth) {
        SystemParameterEventPage page = service.list(com.abluepoint.summer.sample.configuration.ConfigurationTenantContext.resolve(auth), key);
        return body(HttpStatus.OK, Map.of("items", page.items(), "totalElements", page.totalElements()));
    }

    @PostMapping("/{eventId}/retry")
    @com.abluepoint.summer.sample.resource.ResourcePermission("system.parameter.update")
    @RawResponse
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.update','SCOPE_platform.parameter.update','SCOPE_system.parameter.write','SCOPE_platform.parameter.write','SCOPE_customer.write')")
    ResponseEntity<ApiResponse<SystemParameterEventView>> retry(@PathVariable String eventId, org.springframework.security.core.Authentication auth) {
        try {
            return body(HttpStatus.OK, service.retry(com.abluepoint.summer.sample.configuration.ConfigurationTenantContext.resolve(auth), eventId));
        } catch (SystemParameterEventNotFoundException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", exception.getMessage());
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus status, T value) {
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.success(value, requestId.currentRequestId()));
    }

    private <T> ResponseEntity<ApiResponse<T>> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.failure(new ApiError(code, message, List.of()), requestId.currentRequestId()));
    }
}
