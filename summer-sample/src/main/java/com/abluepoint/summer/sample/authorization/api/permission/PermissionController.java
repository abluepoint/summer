package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.mvc.annotation.Field;

import com.abluepoint.summer.common.response.*;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionManagementGuard;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionService;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RawResponse
@RequestMapping("/api/v1/platform")
public class PermissionController {
    private final PermissionService service;
    private final PermissionManagementGuard guard;
    private final RequestIdAccessor requestId;

    public PermissionController(
            PermissionService service,
            PermissionManagementGuard guard,
            RequestIdAccessor requestId) {
        this.service = service;
        this.guard = guard;
        this.requestId = requestId;
    }

    private static String pattern(HttpServletRequest r) {
        Object value =
                r.getAttribute(
                        org.springframework.web.servlet.HandlerMapping
                                .BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (value instanceof org.springframework.web.util.pattern.PathPattern p)
            return p.getPatternString();
        if (value instanceof String p) return p;
        throw new org.springframework.security.access.AccessDeniedException(
                "Matched mapping required");
    }

    public static Domain domain(HttpServletRequest r) {
        Object value =
                r.getAttribute(
                        org.springframework.web.servlet.HandlerMapping
                                .URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (value instanceof Map<?, ?> vars && vars.get("enterpriseId") instanceof String id)
            return Domain.enterprise(id);
        if (pattern(r).startsWith("/api/v1/platform/")) return Domain.platform();
        throw new org.springframework.security.access.AccessDeniedException(
                "Matched domain required");
    }

    private boolean group(HttpServletRequest r) {
        String p = pattern(r);
        return p.endsWith("/permission-groups") || p.endsWith("/permission-groups/{id}");
    }

    @GetMapping({"/permission-groups", "/permissions"})
    public Object list(
            HttpServletRequest r, Authentication a, @RequestParam Map<String, String> query) {
        Domain d = domain(r);
        guard.require(d, a, false);
        return ApiResponse.success(service.list(Domain.platform(), group(r), query), requestId.currentRequestId());
    }

    @Field(param = "id", style = "requestTextStyle")
    @GetMapping({"/permission-groups/{id}", "/permissions/{id}"})
    public Object get(HttpServletRequest r, Authentication a, @PathVariable String id) {
        guard.require(domain(r), a, false);
        return response(service.get(service.readDomain(domain(r), group(r), id), group(r), id));
    }

    @PostMapping({"/permission-groups", "/permissions"})
    public Object create(
            HttpServletRequest r,
            Authentication a,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return mutate(r, a, null, body, null, key);
    }

    @PatchMapping({"/permission-groups/{id}", "/permissions/{id}"})
    public Object patch(
            HttpServletRequest r,
            Authentication a,
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "If-Match", required = false) String condition,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return mutate(r, a, id, body, condition, key);
    }

    @Field(param = "id", style = "requestTextStyle")
    @DeleteMapping({"/permission-groups/{id}", "/permissions/{id}"})
    public Object delete(
            HttpServletRequest r,
            Authentication a,
            @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String condition,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return mutate(r, a, id, Map.of(), condition, key);
    }

    private Object mutate(
            HttpServletRequest r,
            Authentication a,
            String id,
            Map<String, Object> body,
            String condition,
            String key) {
        Domain d = domain(r);
        guard.requireDefinitionWrite(d, a);
        return response(
                service.mutate(
                        d,
                        a.getName(),
                        group(r),
                        id,
                        r.getMethod(),
                        body,
                        condition,
                        key,
                        requestId.currentRequestId()));
    }

    private Object response(PermissionService.Result r) {
        return ResponseEntity.status(r.status())
                .eTag("\"" + r.version() + "\"")
                .body(ApiResponse.success(r.body(), requestId.currentRequestId()));
    }
}
