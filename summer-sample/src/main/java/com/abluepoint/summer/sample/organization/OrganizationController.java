package com.abluepoint.summer.sample.organization;

import com.abluepoint.summer.common.response.ApiError;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
class OrganizationController {
    @org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    @RawResponse
    ResponseEntity<ApiResponse<Void>> statusFailure(org.springframework.web.server.ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.failure(new ApiError("SUMMER_DIRECTORY_REQUEST_REJECTED", error.getReason() == null ? "Directory resource was not found" : error.getReason(), List.of()), requestId.currentRequestId()));
    }

    private final OrganizationDirectoryService service;
    private final RequestIdAccessor requestId;

    OrganizationController(OrganizationDirectoryService service, RequestIdAccessor requestId) {
        this.service = service;
        this.requestId = requestId;
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/org-tree")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.organization.read")
    @PreAuthorize("hasAnyAuthority('SCOPE_enterprise.organization.read', 'SCOPE_enterprise.organizations.read')")
    @RawResponse
    ResponseEntity<ApiResponse<OrganizationTree>> tree(@PathVariable String enterpriseId, Authentication authentication, HttpServletRequest request) {
        return response(service.tree(enterpriseId, authentication), request);
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/org-units")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.organization.read")
    @PreAuthorize("hasAnyAuthority('SCOPE_enterprise.organization.read', 'SCOPE_enterprise.organizations.read')")
    @RawResponse
    ResponseEntity<ApiResponse<OrganizationTree>> list(@PathVariable String enterpriseId, @RequestParam(required = false) String parentId,
                                                        @RequestParam(required = false) String q, Authentication authentication, HttpServletRequest request) {
        return response(service.list(enterpriseId, parentId, q, authentication), request);
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/org-units/{unitId}")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.organization.read")
    @PreAuthorize("hasAnyAuthority('SCOPE_enterprise.organization.read', 'SCOPE_enterprise.organizations.read')")
    @RawResponse
    ResponseEntity<ApiResponse<OrganizationUnit>> detail(@PathVariable String enterpriseId, @PathVariable String unitId,
                                                          Authentication authentication, HttpServletRequest request) {
        try {
            OrganizationUnit body = service.detail(enterpriseId, unitId, authentication);
            return responseEntity(HttpStatus.OK, body, service.unitRevisionValue(enterpriseId, unitId), request);
        } catch (ResponseStatusException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", "Organization was not found");
        }
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/org-units/{unitId}/members")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.organization.read")
    @PreAuthorize("hasAnyAuthority('SCOPE_enterprise.organization.read', 'SCOPE_enterprise.organizations.read')")
    @RawResponse
    ResponseEntity<ApiResponse<OrganizationMemberList>> members(@PathVariable String enterpriseId, @PathVariable String unitId,
                                                                 Authentication authentication, HttpServletRequest request) {
        try {
            return response(service.members(enterpriseId, unitId, authentication), request);
        } catch (ResponseStatusException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", "Organization was not found");
        }
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/org-units/{unitId}/audit")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.organization.read")
    @PreAuthorize("hasAnyAuthority('SCOPE_enterprise.organization.read', 'SCOPE_enterprise.organizations.read')")
    @RawResponse
    ResponseEntity<ApiResponse<List<OrganizationDirectoryService.OrganizationAuditEvent>>> audit(@PathVariable String enterpriseId,
                                                                                                  @PathVariable String unitId, Authentication authentication) {
        return ResponseEntity.ok().header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.success(service.audit(enterpriseId, unitId, authentication), requestId.currentRequestId()));
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/context")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.organization.read")
    @PreAuthorize("hasAnyAuthority('SCOPE_enterprise.organization.read', 'SCOPE_enterprise.organizations.read')")
    @RawResponse
    ResponseEntity<ApiResponse<OrganizationDirectoryService.CurrentOrganizationContext>> currentContext(
            @PathVariable String enterpriseId, Authentication authentication) {
        return ResponseEntity.ok().header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.success(service.currentContext(enterpriseId, authentication), requestId.currentRequestId()));
    }

    @PutMapping(value = "/api/v1/enterprises/{enterpriseId}/context", consumes = MediaType.APPLICATION_JSON_VALUE)
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.organization.read")
    @PreAuthorize("hasAnyAuthority('SCOPE_enterprise.organization.read', 'SCOPE_enterprise.organizations.read')")
    @RawResponse
    ResponseEntity<ApiResponse<OrganizationDirectoryService.CurrentOrganizationContext>> context(
            @PathVariable String enterpriseId, @RequestBody OrganizationContextRequest body, Authentication authentication) {
        try {
            return ResponseEntity.ok().header("X-Request-Id", requestId.currentRequestId())
                    .body(ApiResponse.success(service.selectContext(enterpriseId, body.organizationId(), authentication), requestId.currentRequestId()));
        } catch (ResponseStatusException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", "Organization was not found");
        }
    }

    @PostMapping(value = "/api/v1/enterprises/{enterpriseId}/org-units", consumes = MediaType.APPLICATION_JSON_VALUE)
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.organization.write")
    @PreAuthorize("hasAnyAuthority('SCOPE_enterprise.organization.write', 'SCOPE_enterprise.organizations.write')")
    @RawResponse
    ResponseEntity<ApiResponse<OrganizationUnit>> create(@PathVariable String enterpriseId, @RequestBody OrganizationUnitRequest body,
                                                          @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                          Authentication authentication, HttpServletRequest request) {
        try {
            OrganizationUnit result = service.create(enterpriseId, body, key, requestId.currentRequestId(), authentication);
            return responseEntity(HttpStatus.CREATED, result, service.unitRevisionValue(enterpriseId, result.id()), request);
        } catch (OrganizationConflictException exception) { return failure(HttpStatus.CONFLICT, "SUMMER_ORGANIZATION_CONFLICT", exception.getMessage());
        } catch (OrganizationValidationException exception) { return failure(HttpStatus.BAD_REQUEST, "SUMMER_ORGANIZATION_INVALID", exception.getMessage());
        } catch (OrganizationIdempotencyConflictException exception) { return failure(HttpStatus.CONFLICT, "SUMMER_IDEMPOTENCY_CONFLICT", exception.getMessage()); }
    }

    @PatchMapping(value = "/api/v1/enterprises/{enterpriseId}/org-units/{unitId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.organization.write")
    @PreAuthorize("hasAnyAuthority('SCOPE_enterprise.organization.write', 'SCOPE_enterprise.organizations.write')")
    @RawResponse
    ResponseEntity<ApiResponse<OrganizationUnit>> update(@PathVariable String enterpriseId, @PathVariable String unitId, @RequestBody OrganizationUnitRequest body,
                                                          @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                          @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                          Authentication authentication, HttpServletRequest request) {
        try {
            OrganizationUnit result = service.update(enterpriseId, unitId, body, ifMatch, key, requestId.currentRequestId(), authentication);
            return responseEntity(HttpStatus.OK, result, service.unitRevisionValue(enterpriseId, result.id()), request);
        } catch (OrganizationPreconditionException exception) { return failure(HttpStatus.PRECONDITION_FAILED, "SUMMER_PRECONDITION_FAILED", "The resource has changed");
        } catch (OrganizationConflictException exception) { return failure(HttpStatus.CONFLICT, "SUMMER_ORGANIZATION_CONFLICT", exception.getMessage());
        } catch (OrganizationValidationException exception) { return failure(HttpStatus.BAD_REQUEST, "SUMMER_ORGANIZATION_INVALID", exception.getMessage());
        } catch (OrganizationIdempotencyConflictException exception) { return failure(HttpStatus.CONFLICT, "SUMMER_IDEMPOTENCY_CONFLICT", exception.getMessage()); }
    }

    @DeleteMapping("/api/v1/enterprises/{enterpriseId}/org-units/{unitId}")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.organization.write")
    @PreAuthorize("hasAnyAuthority('SCOPE_enterprise.organization.write', 'SCOPE_enterprise.organizations.write')")
    @RawResponse
    ResponseEntity<ApiResponse<Void>> delete(@PathVariable String enterpriseId, @PathVariable String unitId,
                                             @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                             @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                             Authentication authentication, HttpServletRequest request) {
        try {
            service.delete(enterpriseId, unitId, ifMatch, key, requestId.currentRequestId(), authentication);
            return ResponseEntity.noContent().header("X-Request-Id", requestId.currentRequestId()).build();
        } catch (OrganizationPreconditionException exception) { return failure(HttpStatus.PRECONDITION_FAILED, "SUMMER_PRECONDITION_FAILED", "The resource has changed");
        } catch (OrganizationConflictException exception) { return failure(HttpStatus.CONFLICT, "SUMMER_ORGANIZATION_CONFLICT", exception.getMessage());
        } catch (OrganizationIdempotencyConflictException exception) { return failure(HttpStatus.CONFLICT, "SUMMER_IDEMPOTENCY_CONFLICT", exception.getMessage()); }
    }

    @PutMapping(value = "/api/v1/enterprises/{enterpriseId}/org-assignments:batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.organization.write")
    @PreAuthorize("hasAnyAuthority('SCOPE_enterprise.organization.write', 'SCOPE_enterprise.organizations.write')")
    @RawResponse
    ResponseEntity<ApiResponse<OrganizationAssignmentBatchResult>> assignBatch(@PathVariable String enterpriseId, @RequestBody OrganizationAssignmentsRequest body,
                                                                                @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                                                @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                                                Authentication authentication, HttpServletRequest request) {
        try {
            OrganizationAssignmentBatchResult result = service.assign(enterpriseId, body, ifMatch, key, requestId.currentRequestId(), authentication);
            return responseEntity(HttpStatus.OK, result, result.revision(), request);
        } catch (OrganizationPreconditionException exception) { return failure(HttpStatus.PRECONDITION_FAILED, "SUMMER_PRECONDITION_FAILED", "The organization revision has changed");
        } catch (OrganizationConflictException exception) { return failure(HttpStatus.CONFLICT, "SUMMER_ORGANIZATION_CONFLICT", exception.getMessage());
        } catch (OrganizationValidationException exception) { return failure(HttpStatus.BAD_REQUEST, "SUMMER_ORGANIZATION_INVALID", exception.getMessage());
        } catch (OrganizationIdempotencyConflictException exception) { return failure(HttpStatus.CONFLICT, "SUMMER_IDEMPOTENCY_CONFLICT", exception.getMessage()); }
    }

    @PutMapping(value = "/api/v1/enterprises/{enterpriseId}/members/{memberId}/org-assignments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.organization.write")
    @PreAuthorize("hasAnyAuthority('SCOPE_enterprise.organization.write', 'SCOPE_enterprise.organizations.write')")
    @RawResponse
    ResponseEntity<ApiResponse<OrganizationAssignmentBatchResult>> assignMember(@PathVariable String enterpriseId, @PathVariable String memberId,
                                                                                 @RequestBody OrganizationAssignmentsRequest body,
                                                                                 @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                                                 @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                                                 Authentication authentication, HttpServletRequest request) {
        OrganizationAssignmentsRequest normalized = new OrganizationAssignmentsRequest(List.of(memberId), body.organizationIds(), body.primaryOrganizationId());
        return assignBatch(enterpriseId, normalized, ifMatch, key, authentication, request);
    }

    private ResponseEntity<ApiResponse<OrganizationTree>> response(OrganizationTree body, HttpServletRequest request) { return responseEntity(HttpStatus.OK, body, body.revision(), request); }
    private ResponseEntity<ApiResponse<OrganizationMemberList>> response(OrganizationMemberList body, HttpServletRequest request) { return responseEntity(HttpStatus.OK, body, body.revision(), request); }
    private <T> ResponseEntity<ApiResponse<T>> responseEntity(HttpStatus status, T body, String revision, HttpServletRequest request) {
        String etag = quote(revision);
        if (etag.equals(request.getHeader(HttpHeaders.IF_NONE_MATCH))) return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).header("X-Request-Id", requestId.currentRequestId()).build();
        return ResponseEntity.status(status).eTag(etag).header("X-Request-Id", requestId.currentRequestId()).body(ApiResponse.success(body, requestId.currentRequestId()));
    }

    private <T> ResponseEntity<ApiResponse<T>> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).header("X-Request-Id", requestId.currentRequestId()).body(ApiResponse.failure(new ApiError(code, message, List.of()), requestId.currentRequestId()));
    }

    private static String quote(String value) { return "\"" + value + "\""; }

    record OrganizationContextRequest(String organizationId) {}
}
