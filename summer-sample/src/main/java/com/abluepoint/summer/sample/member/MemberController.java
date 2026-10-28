package com.abluepoint.summer.sample.member;

import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
class MemberController {
    @org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    @RawResponse
    ResponseEntity<ApiResponse<Void>> statusFailure(org.springframework.web.server.ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).header("X-Request-Id", requestId.currentRequestId())
                .body(ApiResponse.failure(new ApiError("SUMMER_DIRECTORY_REQUEST_REJECTED", error.getReason() == null ? "Directory resource was not found" : error.getReason(), List.of()), requestId.currentRequestId()));
    }

    private final MemberService service;
    private final RequestIdAccessor requestId;
    MemberController(MemberService service, RequestIdAccessor requestId) { this.service = service; this.requestId = requestId; }

    @PostMapping(value = "/api/v1/enterprises/{enterpriseId}/members", consumes = "application/json")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.members.write")
    @PreAuthorize("hasAuthority('SCOPE_enterprise.members.write')")
    @RawResponse
    ResponseEntity<ApiResponse<MemberDetail>> create(@PathVariable String enterpriseId,
            @jakarta.validation.Valid @RequestBody MemberCreateRequest body,
            @RequestHeader("Idempotency-Key") String key, Authentication auth) {
        try {
            MemberDetail result = service.create(enterpriseId, body, key, requestId.currentRequestId(), auth);
            return ResponseEntity.status(201).eTag(quote(result.revision())).header("X-Request-Id", requestId.currentRequestId())
                    .body(ApiResponse.success(result, requestId.currentRequestId()));
        } catch (MemberConflictException conflict) {
            return ResponseEntity.status(409).header("X-Request-Id", requestId.currentRequestId())
                    .body(ApiResponse.failure(new ApiError("SUMMER_MEMBER_CONFLICT", conflict.getMessage(), List.of()), requestId.currentRequestId()));
        } catch (IdempotencyConflictException conflict) {
            return ResponseEntity.status(409).header("X-Request-Id", requestId.currentRequestId())
                    .body(ApiResponse.failure(new ApiError("SUMMER_IDEMPOTENCY_CONFLICT", conflict.getMessage(), List.of()), requestId.currentRequestId()));
        }
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/members")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.members.read")
    @PreAuthorize("hasAuthority('SCOPE_enterprise.members.read')")
    @RawResponse
    ResponseEntity<ApiResponse<MemberPage>> list(@PathVariable String enterpriseId, @RequestParam(defaultValue = "0") int page, @RequestParam(required = false) Integer size, @RequestParam(required = false) Integer pageSize, @RequestParam(required = false) String q, @RequestParam(required = false) String search, @RequestParam(required = false) String status, Authentication auth, HttpServletRequest request) {
        MemberPage body = service.list(enterpriseId, page, size != null ? size : (pageSize != null ? pageSize : 20), q != null ? q : search, status, auth);
        if (quote(body.revision()).equals(request.getHeader(HttpHeaders.IF_NONE_MATCH))) {
            return ResponseEntity.status(304).eTag(quote(body.revision())).header("X-Request-Id", requestId.currentRequestId()).build();
        }
        return ResponseEntity.ok().eTag(quote(body.revision())).header("X-Request-Id", requestId.currentRequestId()).body(ApiResponse.success(body, requestId.currentRequestId()));
    }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/members/{memberId}")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.members.read")
    @PreAuthorize("hasAuthority('SCOPE_enterprise.members.read')")
    @RawResponse
    ResponseEntity<ApiResponse<MemberDetail>> detail(@PathVariable String enterpriseId, @PathVariable String memberId, Authentication auth, HttpServletRequest request) {
        MemberDetail body = service.detail(enterpriseId, memberId, auth);
        if (quote(body.revision()).equals(request.getHeader(HttpHeaders.IF_NONE_MATCH))) {
            return ResponseEntity.status(304).eTag(quote(body.revision())).header("X-Request-Id", requestId.currentRequestId()).build();
        }
        return ResponseEntity.ok().eTag(quote(body.revision())).header("X-Request-Id", requestId.currentRequestId()).body(ApiResponse.success(body, requestId.currentRequestId()));
    }

    @PostMapping("/api/v1/enterprises/{enterpriseId}/members/{memberId}:disable")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.members.write")
    @PreAuthorize("hasAuthority('SCOPE_enterprise.members.write')")
    @RawResponse
    ResponseEntity<ApiResponse<MemberDetail>> disable(@PathVariable String enterpriseId, @PathVariable String memberId, @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch, @RequestHeader("Idempotency-Key") String idempotencyKey, Authentication auth, HttpServletRequest request) { return change(enterpriseId, memberId, "DISABLED", ifMatch, idempotencyKey, auth); }

    @PostMapping("/api/v1/enterprises/{enterpriseId}/members/{memberId}:enable")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.members.write")
    @PreAuthorize("hasAuthority('SCOPE_enterprise.members.write')")
    @RawResponse
    ResponseEntity<ApiResponse<MemberDetail>> enable(@PathVariable String enterpriseId, @PathVariable String memberId, @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch, @RequestHeader("Idempotency-Key") String idempotencyKey, Authentication auth, HttpServletRequest request) { return change(enterpriseId, memberId, "ACTIVE", ifMatch, idempotencyKey, auth); }

    @PostMapping("/api/v1/enterprises/{enterpriseId}/members/{memberId}:leave")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.members.write")
    @PreAuthorize("hasAuthority('SCOPE_enterprise.members.write')")
    @RawResponse
    ResponseEntity<ApiResponse<MemberDetail>> leave(@PathVariable String enterpriseId, @PathVariable String memberId, @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch, @RequestHeader("Idempotency-Key") String idempotencyKey, Authentication auth) { return change(enterpriseId, memberId, "RESIGNED", ifMatch, idempotencyKey, auth); }

    @PostMapping("/api/v1/enterprises/{enterpriseId}/members/{memberId}:resign")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.members.write")
    @PreAuthorize("hasAuthority('SCOPE_enterprise.members.write')")
    @RawResponse
    ResponseEntity<ApiResponse<MemberDetail>> resign(@PathVariable String enterpriseId, @PathVariable String memberId, @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch, @RequestHeader("Idempotency-Key") String idempotencyKey, Authentication auth) { return change(enterpriseId, memberId, "RESIGNED", ifMatch, idempotencyKey, auth); }

    @GetMapping("/api/v1/enterprises/{enterpriseId}/members/{memberId}/audit")
    @com.abluepoint.summer.sample.resource.ResourcePermission("enterprise.members.read")
    @PreAuthorize("hasAuthority('SCOPE_enterprise.members.read')")
    @RawResponse
    ResponseEntity<ApiResponse<List<MemberService.AuditEvent>>> audit(@PathVariable String enterpriseId, @PathVariable String memberId, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(service.audit(enterpriseId, memberId, auth), requestId.currentRequestId()));
    }

    private ResponseEntity<ApiResponse<MemberDetail>> change(String e, String m, String s, String ifMatch, String idempotencyKey, Authentication a) {
        try {
            MemberDetail b = service.changeStatus(e, m, s, ifMatch, idempotencyKey, requestId.currentRequestId(), a);
            return ResponseEntity.ok().eTag(quote(b.revision())).body(ApiResponse.success(b, requestId.currentRequestId()));
        } catch (PreconditionFailedException ex) {
            ApiError error = new ApiError("SUMMER_PRECONDITION_FAILED", "The resource has changed", List.of());
            return ResponseEntity.status(412).body(ApiResponse.failure(error, requestId.currentRequestId()));
        } catch (IdempotencyConflictException ex) {
            ApiError error = new ApiError("SUMMER_IDEMPOTENCY_CONFLICT", ex.getMessage(), List.of());
            return ResponseEntity.status(409).body(ApiResponse.failure(error, requestId.currentRequestId()));
        }
    }
    private static String quote(String v) { return "\"" + v + "\""; }
}

record MemberCreateRequest(
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 128) String subject,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String displayName,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max = 254) String email) {}
