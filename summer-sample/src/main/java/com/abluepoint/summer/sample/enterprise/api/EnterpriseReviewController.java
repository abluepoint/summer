package com.abluepoint.summer.sample.enterprise.api;

import com.abluepoint.summer.sample.enterprise.application.EnterpriseReviewView;
import com.abluepoint.summer.sample.enterprise.application.EnterpriseReviewService;
import com.abluepoint.summer.sample.enterprise.application.EnterpriseReviewDecisionView;
import com.abluepoint.summer.sample.enterprise.domain.ReviewPreconditionException;
import com.abluepoint.summer.sample.enterprise.domain.ReviewConflictException;
import com.abluepoint.summer.sample.enterprise.domain.ReviewValidationException;

import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class EnterpriseReviewController {
    private final EnterpriseReviewService service; private final RequestIdAccessor requestId;
    EnterpriseReviewController(EnterpriseReviewService service, RequestIdAccessor requestId) { this.service = service; this.requestId = requestId; }
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("platform.enterprise-review.read")
    @GetMapping("/api/v1/platform/enterprise-reviews") @PreAuthorize("hasAuthority('SCOPE_platform.enterprise-review.read')") @RawResponse
    ResponseEntity<ApiResponse<ListResponse>> list(Authentication auth, HttpServletRequest request) { List<EnterpriseReviewView> items = service.list(auth); return ResponseEntity.ok().eTag(quote(java.util.UUID.nameUUIDFromBytes(items.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString())).header("X-Request-Id", requestId.currentRequestId()).body(ApiResponse.success(new ListResponse(items), requestId.currentRequestId())); }
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("platform.enterprise-review.read")
    @GetMapping("/api/v1/platform/enterprise-reviews/{id}") @PreAuthorize("hasAuthority('SCOPE_platform.enterprise-review.read')") @RawResponse
    ResponseEntity<ApiResponse<EnterpriseReviewView>> detail(@PathVariable String id, Authentication auth) { EnterpriseReviewView body = service.detail(id, auth); return ResponseEntity.ok().eTag(quote(body.revision())).body(ApiResponse.success(body, requestId.currentRequestId())); }
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("platform.enterprise-review.read")
    @GetMapping("/api/v1/platform/enterprise-reviews/{id}/audit") @PreAuthorize("hasAuthority('SCOPE_platform.enterprise-review.read')") @RawResponse
    ResponseEntity<ApiResponse<List<EnterpriseReviewDecisionView>>> audit(@PathVariable String id, Authentication auth) { return ResponseEntity.ok(ApiResponse.success(service.audit(id, auth), requestId.currentRequestId())); }
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("platform.enterprise-review.write")
    @PostMapping("/api/v1/platform/enterprise-reviews/{id}:approve") @PreAuthorize("hasAuthority('SCOPE_platform.enterprise-review.write')") @RawResponse
    ResponseEntity<ApiResponse<EnterpriseReviewView>> approve(@PathVariable String id, @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch, @RequestHeader("Idempotency-Key") String key, Authentication auth) { return decide(id, "APPROVE", null, ifMatch, key, auth); }
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("platform.enterprise-review.write")
    @PostMapping("/api/v1/platform/enterprise-reviews/{id}:reject") @PreAuthorize("hasAuthority('SCOPE_platform.enterprise-review.write')") @RawResponse
    ResponseEntity<ApiResponse<EnterpriseReviewView>> reject(@PathVariable String id, @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch, @RequestHeader("Idempotency-Key") String key, @RequestBody EnterpriseReviewRequest body, Authentication auth) { return decide(id, "REJECT", body.reason(), ifMatch, key, auth); }
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("platform.enterprise-review.write")
    @PostMapping("/api/v1/platform/enterprise-reviews/{id}:request-info") @PreAuthorize("hasAuthority('SCOPE_platform.enterprise-review.write')") @RawResponse
    ResponseEntity<ApiResponse<EnterpriseReviewView>> requestInfo(@PathVariable String id, @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch, @RequestHeader("Idempotency-Key") String key, @RequestBody EnterpriseReviewRequest body, Authentication auth) { return decide(id, "REQUEST_INFO", body.reason(), ifMatch, key, auth); }
    private ResponseEntity<ApiResponse<EnterpriseReviewView>> decide(String id, String decision, String reason, String ifMatch, String key, Authentication auth) { try { EnterpriseReviewView body = service.decide(id, decision, reason, ifMatch, key, requestId.currentRequestId(), auth); return ResponseEntity.ok().eTag(quote(body.revision())).body(ApiResponse.success(body, requestId.currentRequestId())); } catch (ReviewPreconditionException e) { return ResponseEntity.status(412).body(ApiResponse.failure(new ApiError("SUMMER_PRECONDITION_FAILED", "The resource has changed", List.of()), requestId.currentRequestId())); } catch (ReviewConflictException e) { return ResponseEntity.status(409).body(ApiResponse.failure(new ApiError("SUMMER_IDEMPOTENCY_CONFLICT", e.getMessage(), List.of()), requestId.currentRequestId())); } catch (ReviewValidationException e) { return ResponseEntity.badRequest().body(ApiResponse.failure(new ApiError("SUMMER_INVALID_REVIEW", e.getMessage(), List.of()), requestId.currentRequestId())); } }
    record ListResponse(List<EnterpriseReviewView> items) { ListResponse { items = List.copyOf(items); } }
    private static String quote(String v) { return "\"" + v + "\""; }
}
