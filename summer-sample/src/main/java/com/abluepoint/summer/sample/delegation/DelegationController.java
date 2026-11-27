package com.abluepoint.summer.sample.delegation;

import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.common.error.CommonErrorCode;import com.abluepoint.summer.common.error.SummerException;import com.abluepoint.summer.common.response.*;import com.abluepoint.summer.mvc.api.RawResponse;import com.abluepoint.summer.mvc.request.RequestIdAccessor;import org.springframework.http.*;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @PreAuthorize("isAuthenticated()") class DelegationController{final DelegationService s;final RequestIdAccessor r;DelegationController(DelegationService s,RequestIdAccessor r){this.s=s;this.r=r;}
 @PostMapping(value="/api/v1/enterprises/{e}/agents/{a}/delegations",consumes=MediaType.APPLICATION_JSON_VALUE)@RawResponse ResponseEntity<ApiResponse<DelegationGrantView>> create(@PathVariable String e,@PathVariable String a,@RequestBody DelegationCreateRequest q,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication x){try{var v=s.create(e,a,q,k,r.currentRequestId(),x);return body(HttpStatus.CREATED,v,v.version());}catch(org.springframework.security.access.AccessDeniedException z){throw SummerException.withMessage(CommonErrorCode.FORBIDDEN,z.getMessage(),z);}}
 @Field(param = "e", style = "requestTextStyle")
 @Field(param = "a", style = "requestTextStyle")
 @GetMapping("/api/v1/enterprises/{e}/agents/{a}/delegations")@RawResponse ResponseEntity<ApiResponse<List<DelegationGrantView>>> list(@PathVariable String e,@PathVariable String a,Authentication x){try{return body(HttpStatus.OK,s.list(e,a,x),null);}catch(org.springframework.security.access.AccessDeniedException z){throw SummerException.withMessage(CommonErrorCode.FORBIDDEN,z.getMessage(),z);}}
 @Field(param = "e", style = "requestTextStyle")
 @Field(param = "id", style = "requestTextStyle")
 @PostMapping("/api/v1/enterprises/{e}/delegations/{id}:revoke")@RawResponse ResponseEntity<ApiResponse<DelegationGrantView>> revoke(@PathVariable String e,@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication x){try{var v=s.revoke(e,id,k,r.currentRequestId(),x);return body(HttpStatus.OK,v,v.version());}catch(org.springframework.security.access.AccessDeniedException z){throw SummerException.withMessage(CommonErrorCode.FORBIDDEN,z.getMessage(),z);}}
 @Field(param = "enterpriseId", required = false, nullable = true, style = "requestTextStyle")
 @Field(param = "agentId", required = false, nullable = true, style = "requestTextStyle")
 @Field(param = "agentInstanceId", required = false, nullable = true, style = "requestTextStyle")
 @Field(param = "mcpServerId", required = false, nullable = true, style = "requestTextStyle")
 @Field(param = "toolId", required = false, nullable = true, style = "requestTextStyle")
 @Field(param = "requestDigest", required = false, nullable = true, style = "requestTextStyle")
 @Field(param = "approvalScope", required = false, nullable = true, style = "requestTextStyle")
 @Field(param = "expiresInSeconds", required = false, nullable = true, style = "requestLongStyle")
 @Field(param = "approverPrincipalId", required = false, nullable = true, style = "requestTextStyle")
 @PostMapping(value="/internal/v1/mcp/approvals",consumes=MediaType.APPLICATION_JSON_VALUE)@RawResponse ResponseEntity<ApiResponse<ApprovalView>> approval(@RequestBody ApprovalCreateRequest q,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication x){try{return body(HttpStatus.CREATED,s.createApproval(q,k,r.currentRequestId(),x),null);}catch(org.springframework.security.access.AccessDeniedException z){throw SummerException.withMessage(CommonErrorCode.FORBIDDEN,z.getMessage(),z);}}
 @Field(param = "id", style = "requestTextStyle")
 @PostMapping("/api/v1/mcp/approvals/{id}:approve")@RawResponse ResponseEntity<ApiResponse<ApprovalView>> approve(@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication x){return decide(id,"APPROVE",k,x);}
 @Field(param = "id", style = "requestTextStyle")
 @PostMapping("/api/v1/mcp/approvals/{id}:reject")@RawResponse ResponseEntity<ApiResponse<ApprovalView>> reject(@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication x){return decide(id,"REJECT",k,x);}
 @Field(param = "id", style = "requestTextStyle")
 @PostMapping("/api/v1/mcp/approvals/{id}:withdraw")@RawResponse ResponseEntity<ApiResponse<ApprovalView>> withdraw(@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication x){return decide(id,"WITHDRAW",k,x);}
 @Field(param = "id", style = "requestTextStyle")
 @PostMapping("/api/v1/mcp/approvals/{id}:recheck")@RawResponse ResponseEntity<ApiResponse<ApprovalView>> recheck(@PathVariable String id,Authentication x){try{return body(HttpStatus.OK,s.recheck(id,x),null);}catch(org.springframework.security.access.AccessDeniedException z){throw SummerException.withMessage(CommonErrorCode.FORBIDDEN,z.getMessage(),z);}}
 @Field(param = "id", style = "requestTextStyle")
 @GetMapping("/api/v1/mcp/approvals/{id}/audit")@RawResponse ResponseEntity<ApiResponse<List<DelegationAuditView>>> audit(@PathVariable String id,Authentication x){try{var a=s.recheck(id,x);return body(HttpStatus.OK,s.audit(a.enterpriseId(),id,x),null);}catch(org.springframework.security.access.AccessDeniedException z){throw SummerException.withMessage(CommonErrorCode.FORBIDDEN,z.getMessage(),z);}}
 private ResponseEntity<ApiResponse<ApprovalView>> decide(String id,String a,String k,Authentication x){try{return body(HttpStatus.OK,s.decide(id,a,k,x),null);}catch(org.springframework.security.access.AccessDeniedException z){throw SummerException.withMessage(CommonErrorCode.FORBIDDEN,z.getMessage(),z);}}
 private<T>ResponseEntity<ApiResponse<T>> body(HttpStatus h,T v,Long ver){var b=ResponseEntity.status(h).header("X-Request-Id",r.currentRequestId());if(ver!=null)b.eTag("\""+ver+"\"");return b.body(ApiResponse.success(v,r.currentRequestId()));}
}
