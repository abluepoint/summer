package com.abluepoint.summer.sample.authorization.api.permission;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.authorization.application.data.ApplicationOperationService;
import com.abluepoint.summer.sample.authorization.application.data.ApplicationQueryService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/enterprises/{enterpriseId}", "/api/v1/platform"})
public class PermissionDataOperationController {
    private final ApplicationOperationService applications;
    private final RequestIdAccessor requests;
    private final ApplicationQueryService queries;

    public PermissionDataOperationController(
            ApplicationOperationService applications,
            RequestIdAccessor requests,
            ApplicationQueryService queries) {
        this.applications = applications;
        this.requests = requests;
        this.queries = queries;
    }

    @PostMapping("/data-records/{resource}")
    @ResponseStatus(HttpStatus.CREATED)
    public Object create(
            HttpServletRequest request,
            Authentication actor,
            @PathVariable String resource,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(
                applications.create(PermissionController.domain(request), resource, actor, body),
                requests.currentRequestId());
    }

    @PatchMapping("/data-records/{resource}/{id}")
    public Object update(
            HttpServletRequest request,
            Authentication actor,
            @PathVariable String resource,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(
                applications.update(
                        PermissionController.domain(request), resource, actor, id, body),
                requests.currentRequestId());
    }

    @DeleteMapping("/data-records/{resource}/{id}")
    public Object delete(
            HttpServletRequest request,
            Authentication actor,
            @PathVariable String resource,
            @PathVariable String id,
            @RequestParam long version) {
        return ApiResponse.success(
                applications.delete(
                        PermissionController.domain(request), resource, actor, id, version),
                requests.currentRequestId());
    }

    @PostMapping("/data-records/{resource}/batch-update")
    public Object batchUpdate(
            HttpServletRequest request,
            Authentication actor,
            @PathVariable String resource,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(
                applications.batch(
                        PermissionController.domain(request), resource, actor, body, false),
                requests.currentRequestId());
    }

    @PostMapping("/data-records/{resource}/batch-delete")
    public Object batchDelete(
            HttpServletRequest request,
            Authentication actor,
            @PathVariable String resource,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(
                applications.batch(
                        PermissionController.domain(request), resource, actor, body, true),
                requests.currentRequestId());
    }

    @PostMapping("/data-records/{resource}/batch-read")
    public Object batchRead(
            HttpServletRequest request,
            Authentication actor,
            @PathVariable String resource,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.success(
                queries.batchRead(PermissionController.domain(request), resource, actor, body),
                requests.currentRequestId());
    }

    @GetMapping("/data-records/{resource}/{id}/cached")
    public Object cached(
            HttpServletRequest request,
            Authentication actor,
            @PathVariable String resource,
            @PathVariable String id) {
        return ApiResponse.success(
                queries.cached(PermissionController.domain(request), resource, actor, id),
                requests.currentRequestId());
    }

    @com.abluepoint.summer.mvc.api.RawResponse
    @GetMapping(value = "/data-records/{resource}/export", produces = "text/csv")
    public Object export(
            HttpServletRequest request,
            Authentication actor,
            @PathVariable String resource,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "1000") int size) {
        var result =
                queries.export(PermissionController.domain(request), resource, actor, page, size);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(
                        org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=applications.csv")
                .header("X-Total-Count", Long.toString(result.total()))
                .body(result.csv());
    }
}
