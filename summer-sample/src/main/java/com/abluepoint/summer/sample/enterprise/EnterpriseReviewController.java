package com.abluepoint.summer.sample.enterprise;

import com.abluepoint.summer.common.response.ApiResponse;
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
class EnterpriseReviewController {
    private final EnterpriseReviewService service; private final RequestIdAccessor requestId;
    EnterpriseReviewController(EnterpriseReviewService service, RequestIdAccessor requestId) { this.service = service; this.requestId = requestId; }
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.enterprise-review.read")
    @GetMapping("/api/v1/platform/enterprise-reviews") @PreAuthorize("hasAuthority('SCOPE_platform.enterprise-review.read')") @RawResponse
    ResponseEntity<ApiResponse<ListResponse>> list(Authentication auth, HttpServletRequest request) { List<EnterpriseReview> items = service.list(auth); return ResponseEntity.ok().eTag(quote(java.util.UUID.nameUUIDFromBytes(items.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString())).header("X-Request-Id", requestId.currentRequestId()).body(ApiResponse.success(new ListResponse(items), requestId.currentRequestId())); }
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.enterprise-review.read")
    @GetMapping("/api/v1/platform/enterprise-reviews/{id}") @PreAuthorize("hasAuthority('SCOPE_platform.enterprise-review.read')") @RawResponse
    ResponseEntity<ApiResponse<EnterpriseReview>> detail(@PathVariable String id, Authentication auth) { EnterpriseReview body = service.detail(id, auth); return ResponseEntity.ok().eTag(quote(body.revision())).body(ApiResponse.success(body, requestId.currentRequestId())); }
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.enterprise-review.read")
    @GetMapping("/api/v1/platform/enterprise-reviews/{id}/audit") @PreAuthorize("hasAuthority('SCOPE_platform.enterprise-review.read')") @RawResponse
    ResponseEntity<ApiResponse<List<ReviewDecision>>> audit(@PathVariable String id, Authentication auth) { return ResponseEntity.ok(ApiResponse.success(service.audit(id, auth), requestId.currentRequestId())); }
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.enterprise-review.write")
    @PostMapping("/api/v1/platform/enterprise-reviews/{id}:approve") @PreAuthorize("hasAuthority('SCOPE_platform.enterprise-review.write')") @RawResponse
    ResponseEntity<ApiResponse<EnterpriseReview>> approve(@PathVariable String id, @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch, @RequestHeader("Idempotency-Key") String key, Authentication auth) { return decide(id, "APPROVE", null, ifMatch, key, auth); }
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.enterprise-review.write")
    @PostMapping("/api/v1/platform/enterprise-reviews/{id}:reject") @PreAuthorize("hasAuthority('SCOPE_platform.enterprise-review.write')") @RawResponse
    ResponseEntity<ApiResponse<EnterpriseReview>> reject(@PathVariable String id, @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch, @RequestHeader("Idempotency-Key") String key, @RequestBody ReviewRequest body, Authentication auth) { return decide(id, "REJECT", body.reason(), ifMatch, key, auth); }
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.enterprise-review.write")
    @PostMapping("/api/v1/platform/enterprise-reviews/{id}:request-info") @PreAuthorize("hasAuthority('SCOPE_platform.enterprise-review.write')") @RawResponse
    ResponseEntity<ApiResponse<EnterpriseReview>> requestInfo(@PathVariable String id, @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch, @RequestHeader("Idempotency-Key") String key, @RequestBody ReviewRequest body, Authentication auth) { return decide(id, "REQUEST_INFO", body.reason(), ifMatch, key, auth); }
    private ResponseEntity<ApiResponse<EnterpriseReview>> decide(String id, String decision, String reason, String ifMatch, String key, Authentication auth) {
        EnterpriseReview body = service.decide(id, decision, reason, ifMatch, key, requestId.currentRequestId(), auth);
        return ResponseEntity.ok().eTag(quote(body.revision())).body(ApiResponse.success(body, requestId.currentRequestId()));
    }
    record ListResponse(List<EnterpriseReview> items) { ListResponse { items = List.copyOf(items); } }
    private static String quote(String v) { return "\"" + v + "\""; }
}
