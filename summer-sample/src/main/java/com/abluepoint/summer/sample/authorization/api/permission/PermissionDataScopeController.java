package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.mvc.annotation.Field;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionManagementGuard;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RawResponse
@RequestMapping("/api/v1/platform/permissions/{id}/data-scope")
public class PermissionDataScopeController {
    private final PermissionService service;
    private final PermissionManagementGuard guard;
    private final RequestIdAccessor requests;

    public PermissionDataScopeController(
            PermissionService service,
            PermissionManagementGuard guard,
            RequestIdAccessor requests) {
        this.service = service;
        this.guard = guard;
        this.requests = requests;
    }

    @Field(param = "id", style = "requestTextStyle")
    @GetMapping
    public Object get(HttpServletRequest r, Authentication a, @PathVariable String id) {
        var domain = PermissionController.domain(r);
        guard.require(domain, a, false);
        return response(service.getDataScope(service.readDomain(domain, false, id), id));
    }

    @PutMapping
    public Object replace(
            HttpServletRequest r,
            Authentication a,
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "If-Match", required = false) String condition,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        var domain = PermissionController.domain(r);
        guard.requireDefinitionWrite(domain, a);
        return response(
                service.replaceDataScope(
                        domain,
                        a.getName(),
                        id,
                        body,
                        condition,
                        key,
                        requests.currentRequestId()));
    }

    private Object response(PermissionService.Result r) {
        return ResponseEntity.status(r.status())
                .eTag("\"" + r.version() + "\"")
                .body(ApiResponse.success(r.body(), requests.currentRequestId()));
    }
}
