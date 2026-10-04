package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.common.response.ApiError;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.resource.ResourcePermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/authorization-events")
class AuthorizationChangeEventController {
    private final AuthorizationChangeEventService service;
    private final AuthorizationService authorization;
    private final RequestIdAccessor requestId;
    AuthorizationChangeEventController(AuthorizationChangeEventService service, AuthorizationService authorization, RequestIdAccessor requestId) {
        this.service = service; this.authorization = authorization; this.requestId = requestId;
    }

    @GetMapping
    @RawResponse
    @ResourcePermission("enterprise.roles.read")
    ResponseEntity<ApiResponse<Map<String, Object>>> list(@RequestParam(defaultValue = "ent-acme") String enterpriseId,
                                                            @RequestParam(required = false) String resourceType,
                                                            Authentication authentication) {
        try {
            authorization.requireEnterpriseMenu(enterpriseId, authentication, "enterprise.roles.read");
            AuthorizationChangeEventPage page = service.list(enterpriseId, resourceType);
            return body(HttpStatus.OK, Map.of("items", page.items(), "totalElements", page.totalElements()));
        } catch (Exception exception) { return error(exception); }
    }

    @PostMapping("/{eventId}/retry")
    @RawResponse
    @ResourcePermission("enterprise.roles.write")
    ResponseEntity<ApiResponse<AuthorizationChangeEventView>> retry(@PathVariable String eventId,
                                                                      @RequestParam(defaultValue = "ent-acme") String enterpriseId,
                                                                      Authentication authentication) {
        try {
            authorization.requireEnterpriseMenu(enterpriseId, authentication, "enterprise.roles.write");
            return body(HttpStatus.OK, service.retry(enterpriseId, eventId));
        } catch (Exception exception) { return error(exception); }
    }

    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus status, T value) {
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId()).body(ApiResponse.success(value, requestId.currentRequestId()));
    }
    private <T> ResponseEntity<ApiResponse<T>> error(Exception exception) {
        HttpStatus status = exception instanceof AccessDeniedException ? HttpStatus.FORBIDDEN
                : exception instanceof AuthorizationChangeEventNotFoundException ? HttpStatus.NOT_FOUND : HttpStatus.INTERNAL_SERVER_ERROR;
        String code = status == HttpStatus.FORBIDDEN ? "SUMMER_FORBIDDEN" : status == HttpStatus.NOT_FOUND ? "SUMMER_NOT_FOUND" : "SUMMER_INTERNAL_ERROR";
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.failure(new ApiError(code, exception.getMessage(), List.of()), requestId.currentRequestId()));
    }
}
