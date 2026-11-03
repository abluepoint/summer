package com.abluepoint.summer.sample.mcpauthorization;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import org.springframework.security.access.AccessDeniedException;
import java.util.function.Supplier;
import com.abluepoint.summer.common.response.*;import com.abluepoint.summer.mvc.api.RawResponse;import com.abluepoint.summer.mvc.request.RequestIdAccessor;import org.springframework.http.*;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController @PreAuthorize("isAuthenticated()") class McpAuthorizationController{final McpAuthorizationService s;final RequestIdAccessor r;McpAuthorizationController(McpAuthorizationService s,RequestIdAccessor r){this.s=s;this.r=r;}
 @PostMapping(value="/internal/v1/mcp/execution-decisions",consumes=MediaType.APPLICATION_JSON_VALUE)@RawResponse ResponseEntity<ApiResponse<ExecutionDecisionView>> decide(@RequestBody ExecutionDecisionRequest q,Authentication a){return body(HttpStatus.OK,preserveAccessDenied(() -> s.decide(q,a)));}
 @PostMapping(value="/internal/v1/mcp/execution-permits/{id}:claim",consumes=MediaType.APPLICATION_JSON_VALUE)@RawResponse ResponseEntity<ApiResponse<PermitClaimView>> claim(@PathVariable String id,@RequestBody PermitClaimRequest q,Authentication a){return body(HttpStatus.OK,preserveAccessDenied(() -> s.claim(id,q,a)));}
 @PostMapping("/api/v1/enterprises/{e}/mcp-execution-permits/{id}:revoke")@RawResponse ResponseEntity<ApiResponse<ExecutionPermitView>> revoke(@PathVariable String e,@PathVariable String id,Authentication a){return body(HttpStatus.OK,preserveAccessDenied(() -> s.revoke(e,id,a)));}
 private<T>ResponseEntity<ApiResponse<T>>body(HttpStatus h,T v){return ResponseEntity.status(h).header("X-Request-Id",r.currentRequestId()).body(ApiResponse.success(v,r.currentRequestId()));}

    /** Preserve this application's original security message without changing the native service contract. */
    private <T> T preserveAccessDenied(Supplier<T> action) {
        try {
            return action.get();
        } catch (AccessDeniedException denied) {
            throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, denied.getMessage(), denied);
        }
    }
}
