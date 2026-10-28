package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
class DataScopeController {
    private final DataScopeService service;
    private final RequestIdAccessor requestId;

    DataScopeController(DataScopeService service, RequestIdAccessor requestId) {
        this.service = service;
        this.requestId = requestId;
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/data-scopes")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.data-scope.read")
    @RawResponse
    ResponseEntity<ApiResponse<List<DataScopePolicyView>>> list(@PathVariable String enterpriseId, Authentication auth) {
        try { return ok(service.list(enterpriseId, auth)); } catch (Exception e) { return error(e); }
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/data-scopes/{resource}")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.data-scope.read")
    @RawResponse
    ResponseEntity<ApiResponse<DataScopePolicyView>> get(@PathVariable String enterpriseId, @PathVariable String resource, Authentication auth) {
        try { return ok(service.get(enterpriseId, resource, auth)); } catch (Exception e) { return error(e); }
    }

    @PutMapping(value = "/api/v1/enterprises/{enterpriseId}/data-scopes/{resource}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.data-scope.write")
    @RawResponse
    ResponseEntity<ApiResponse<DataScopePolicyView>> put(@PathVariable String enterpriseId, @PathVariable String resource,
                                                          @RequestBody DataScopePolicyRequest body, Authentication auth) {
        try { return ok(service.put(enterpriseId, resource, body, requestId(), auth)); } catch (Exception e) { return error(e); }
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/data-scopes/{resource}/audit")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.data-scope.read")
    @RawResponse
    ResponseEntity<ApiResponse<List<DataScopeAuditView>>> audit(@PathVariable String enterpriseId, @PathVariable String resource, Authentication auth) {
        try { return ok(service.audit(enterpriseId, resource, auth)); } catch (Exception e) { return error(e); }
    }

    @PostMapping(value = "/api/v1/enterprises/{enterpriseId}/data-scopes/{resource}:preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.data-scope.read")
    @RawResponse
    ResponseEntity<ApiResponse<DataScopePreviewView>> preview(@PathVariable String enterpriseId, @PathVariable String resource,
                                                               @RequestBody(required = false) DataScopePreviewRequest request, Authentication auth) {
        try { return ok(service.preview(enterpriseId, resource, request, auth)); } catch (Exception e) { return error(e); }
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/data-scopes/{resource}/preview")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.data-scope.read")
    @RawResponse
    ResponseEntity<ApiResponse<DataScopePreviewView>> previewGet(@PathVariable String enterpriseId, @PathVariable String resource,
                                                                   @RequestParam(required = false) String subject,
                                                                   @RequestParam(required = false) String organizationId,
                                                                   Authentication auth) {
        try { return ok(service.preview(enterpriseId, resource, new DataScopePreviewRequest(subject, organizationId, null, null, List.of(), List.of()), auth)); }
        catch (Exception e) { return error(e); }
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/data-scopes/{resource}/bindings")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.data-scope.read")
    @RawResponse
    ResponseEntity<ApiResponse<List<DataScopeBindingView>>> bindings(@PathVariable String enterpriseId, @PathVariable String resource, Authentication auth) {
        try { return ok(service.bindings(enterpriseId, resource, auth)); } catch (Exception e) { return error(e); }
    }

    @PutMapping("/api/v1/enterprises/{enterpriseId}/data-scopes/{resource}/bindings/{kind}/{subject}")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.data-scope.write")
    @RawResponse
    ResponseEntity<ApiResponse<DataScopeBindingView>> bind(@PathVariable String enterpriseId, @PathVariable String resource,
            @PathVariable String kind, @PathVariable String subject, @RequestBody DataScopePolicyRequest body, Authentication auth) {
        try { return ok(service.bind(enterpriseId, resource, kind, subject, body, requestId(), auth)); } catch (Exception e) { return error(e); }
    }

    private String requestId() { return requestId.currentRequestId(); }
    private <T> ResponseEntity<ApiResponse<T>> ok(T body) { return ResponseEntity.ok().header("X-Request-Id", requestId()).body(ApiResponse.success(body, requestId())); }
    private <T> ResponseEntity<ApiResponse<T>> error(Exception exception) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String code = "SUMMER_INTERNAL_ERROR";
        if (exception instanceof AccessDeniedException) { status = HttpStatus.FORBIDDEN; code = "SUMMER_FORBIDDEN"; }
        else if (exception instanceof IllegalArgumentException) { status = HttpStatus.BAD_REQUEST; code = "SUMMER_DATA_SCOPE_INVALID"; }
        else if (exception instanceof NoSuchElementException) { status = HttpStatus.NOT_FOUND; code = "SUMMER_NOT_FOUND"; }
        return ResponseEntity.status(status).header("X-Request-Id", requestId()).body(ApiResponse.failure(new ApiError(code, status == HttpStatus.INTERNAL_SERVER_ERROR ? "An unexpected error occurred" : exception.getMessage(), List.of()), requestId()));
    }
}
