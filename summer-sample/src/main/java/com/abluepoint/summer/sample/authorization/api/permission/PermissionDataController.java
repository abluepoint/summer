package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.authorization.application.data.ApplicationQueryService;
import com.abluepoint.summer.sample.authorization.application.data.DataResourceRegistry;
import com.abluepoint.summer.sample.authorization.application.permission.PermissionManagementGuard;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/enterprises/{enterpriseId}", "/api/v1/platform"})
public class PermissionDataController {
    private final DataResourceRegistry registry;
    private final ApplicationQueryService applications;
    private final com.abluepoint.summer.sample.authorization.application.data.DataResourcePreviewService previews;
    private final PermissionManagementGuard guard;
    private final RequestIdAccessor requests;

    public PermissionDataController(
            DataResourceRegistry registry,
            ApplicationQueryService applications,
            com.abluepoint.summer.sample.authorization.application.data.DataResourcePreviewService previews,
            PermissionManagementGuard guard,
            RequestIdAccessor requests) {
        this.requests = requests;
        this.registry = registry;
        this.applications = applications;
        this.previews = previews;
        this.guard = guard;
    }

    @GetMapping("/data-resources")
    public Object resources(HttpServletRequest r, Authentication a) {
        guard.require(PermissionController.domain(r), a, false);
        return ApiResponse.success(registry.list(PermissionController.domain(r)), requests.currentRequestId());
    }

    @GetMapping("/data-records/{resource}")
    public Object list(
            HttpServletRequest r,
            Authentication a,
            @PathVariable String resource,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                applications.list(PermissionController.domain(r), resource, a, page, size, false),
                requests.currentRequestId());
    }

    @GetMapping("/data-records/{resource}/jpa")
    public Object jpa(
            HttpServletRequest r,
            Authentication a,
            @PathVariable String resource,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                applications.list(PermissionController.domain(r), resource, a, page, size, true),
                requests.currentRequestId());
    }

    @GetMapping("/data-records/{resource}/{id}")
    public Object detail(
            HttpServletRequest r,
            Authentication a,
            @PathVariable String resource,
            @PathVariable String id) {
        return ApiResponse.success(
                applications.detail(PermissionController.domain(r), resource, a, id),
                requests.currentRequestId());
    }

    @PostMapping("/data-resources/{resource}/preview")
    public Object preview(
            HttpServletRequest r,
            Authentication a,
            @PathVariable String resource,
            @RequestBody Map<String, Object> body) {
        var domain = PermissionController.domain(r);
        guard.require(domain, a, false);
        return ApiResponse.success(
                previews.preview(domain, resource, body), requests.currentRequestId());
    }
}
