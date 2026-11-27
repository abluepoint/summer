package com.abluepoint.summer.sample.invitation;

import com.abluepoint.summer.mvc.annotation.Field;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
class InvitationController {
    private final InvitationService service; private final RequestIdAccessor requestId;
    InvitationController(InvitationService service, RequestIdAccessor requestId) { this.service = service; this.requestId = requestId; }

    @PostMapping(value="/api/v1/enterprises/{enterpriseId}/invitations", consumes=MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_enterprise.invitation.write')") @RawResponse
    ResponseEntity<ApiResponse<InvitationIssue>> create(@PathVariable String enterpriseId,@RequestBody InvitationCreateRequest body,@RequestHeader(value="Idempotency-Key",required=false) String key,Authentication auth) {  InvitationIssue result = service.create(enterpriseId,body,key,rid(),auth); return entity(HttpStatus.CREATED, result, result.revision());  }

    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "invitationId", style = "requestTextStyle")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/invitations/{invitationId}") @PreAuthorize("hasAuthority('SCOPE_enterprise.invitation.read')") @RawResponse
    ResponseEntity<ApiResponse<InvitationView>> get(@PathVariable String enterpriseId,@PathVariable String invitationId,Authentication auth,HttpServletRequest req){ InvitationView result=service.get(enterpriseId,invitationId,auth);return entity(HttpStatus.OK,result,result.revision(),req); }
    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "search", required = false, style = "requestTextStyle")
    @Field(param = "status", required = false, style = "requestTextStyle")
    @Field(param = "page", required = false, style = "requestIntegerParameterStyle")
    @Field(param = "pageSize", required = false, style = "requestIntegerParameterStyle")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/invitations") @PreAuthorize("hasAuthority('SCOPE_enterprise.invitation.read')") @RawResponse
    ResponseEntity<ApiResponse<InvitationService.Page<InvitationView>>> list(@PathVariable String enterpriseId,@RequestParam(required=false) String search,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,Authentication auth){try{return ok(service.listInvitations(enterpriseId,search,status,page,pageSize,auth));}catch(Exception e){throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, e.getMessage(), e);}}
    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "invitationId", style = "requestTextStyle")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/invitations/{invitationId}/audit") @PreAuthorize("hasAuthority('SCOPE_enterprise.invitation.read')") @RawResponse
    ResponseEntity<ApiResponse<List<InvitationService.AuditEvent>>> audit(@PathVariable String enterpriseId,@PathVariable String invitationId,Authentication auth){return ok(service.audit(enterpriseId,invitationId,auth)); }

    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "invitationId", style = "requestTextStyle")
    @PostMapping("/api/v1/enterprises/{enterpriseId}/invitations/{invitationId}:resend") @PreAuthorize("hasAuthority('SCOPE_enterprise.invitation.write')") @RawResponse
    ResponseEntity<ApiResponse<InvitationIssue>> resend(@PathVariable String enterpriseId,@PathVariable String invitationId,@RequestHeader(value="If-Match",required=false) String etag,@RequestHeader(value="Idempotency-Key",required=false) String key,Authentication auth){InvitationIssue r=service.resend(enterpriseId,invitationId,etag,key,rid(),auth);return entity(HttpStatus.OK,r,r.revision()); }
    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "invitationId", style = "requestTextStyle")
    @PostMapping("/api/v1/enterprises/{enterpriseId}/invitations/{invitationId}:revoke") @PreAuthorize("hasAuthority('SCOPE_enterprise.invitation.write')") @RawResponse
    ResponseEntity<ApiResponse<InvitationView>> revoke(@PathVariable String enterpriseId,@PathVariable String invitationId,@RequestHeader(value="If-Match",required=false) String etag,@RequestHeader(value="Idempotency-Key",required=false) String key,Authentication auth){InvitationView result=service.revoke(enterpriseId,invitationId,etag,key,rid(),auth);return entity(HttpStatus.OK,result,result.revision()); }

    @Field(param = "token", style = "requestTextStyle")
    @Field(param = "enterpriseId", required = false, nullable = true, style = "requestTextStyle")
    @PostMapping("/api/v1/invitations/{token}:accept") @PreAuthorize("hasAuthority('SCOPE_enterprise.invitation.accept')") @RawResponse
    ResponseEntity<ApiResponse<InvitationView>> accept(@PathVariable String token,@RequestBody(required=false) InvitationContextRequest body,@RequestHeader(value="Idempotency-Key",required=false) String key,Authentication auth){return ok(service.accept(token,body,key,rid(),auth)); }
    @Field(param = "token", style = "requestTextStyle")
    @Field(param = "enterpriseId", required = false, nullable = true, style = "requestTextStyle")
    @PostMapping("/api/v1/invitations/{token}:reject") @PreAuthorize("hasAuthority('SCOPE_enterprise.invitation.accept')") @RawResponse
    ResponseEntity<ApiResponse<InvitationView>> rejectToken(@PathVariable String token,@RequestBody(required=false) InvitationContextRequest body,@RequestHeader(value="Idempotency-Key",required=false) String key,Authentication auth){return ok(service.reject(token,body,key,rid(),auth)); }

    @PostMapping(value="/api/v1/enterprises/{enterpriseId}/join-applications", consumes=MediaType.APPLICATION_JSON_VALUE) @PreAuthorize("hasAuthority('SCOPE_enterprise.join-application.submit')") @RawResponse
    ResponseEntity<ApiResponse<JoinApplicationView>> submit(@PathVariable String enterpriseId,@RequestBody JoinApplicationSubmitRequest body,@RequestHeader(value="Idempotency-Key",required=false) String key,Authentication auth){JoinApplicationView r=service.submit(enterpriseId,body,key,rid(),auth);return entity(HttpStatus.CREATED,r,r.revision()); }
    @PatchMapping(value="/api/v1/enterprises/{enterpriseId}/join-applications/{id}", consumes=MediaType.APPLICATION_JSON_VALUE) @PreAuthorize("hasAuthority('SCOPE_enterprise.join-application.submit')") @RawResponse
    ResponseEntity<ApiResponse<JoinApplicationView>> supplement(@PathVariable String enterpriseId,@PathVariable String id,@RequestBody JoinApplicationSupplementRequest body,@RequestHeader(value="If-Match",required=false) String etag,@RequestHeader(value="Idempotency-Key",required=false) String key,Authentication auth){JoinApplicationView r=service.supplement(enterpriseId,id,body,etag,key,rid(),auth);return entity(HttpStatus.OK,r,r.revision()); }
    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "id", style = "requestTextStyle")
    @PostMapping("/api/v1/enterprises/{enterpriseId}/join-applications/{id}:approve") @PreAuthorize("hasAuthority('SCOPE_enterprise.join-application.review')") @RawResponse
    ResponseEntity<ApiResponse<JoinApplicationView>> approve(@PathVariable String enterpriseId,@PathVariable String id,@RequestHeader(value="If-Match",required=false) String etag,@RequestHeader(value="Idempotency-Key",required=false) String key,Authentication auth){JoinApplicationView r=service.approve(enterpriseId,id,etag,key,rid(),auth);return entity(HttpStatus.OK,r,r.revision()); }
    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "id", style = "requestTextStyle")
    @PostMapping("/api/v1/enterprises/{enterpriseId}/join-applications/{id}:reject") @PreAuthorize("hasAuthority('SCOPE_enterprise.join-application.review')") @RawResponse
    ResponseEntity<ApiResponse<JoinApplicationView>> reject(@PathVariable String enterpriseId,@PathVariable String id,@RequestHeader(value="If-Match",required=false) String etag,@RequestHeader(value="Idempotency-Key",required=false) String key,Authentication auth){return entity(HttpStatus.OK,service.reject(enterpriseId,id,etag,key,rid(),auth),etag); }
    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "id", style = "requestTextStyle")
    @PostMapping("/api/v1/enterprises/{enterpriseId}/join-applications/{id}:withdraw") @PreAuthorize("hasAuthority('SCOPE_enterprise.join-application.submit')") @RawResponse
    ResponseEntity<ApiResponse<JoinApplicationView>> withdraw(@PathVariable String enterpriseId,@PathVariable String id,@RequestHeader(value="If-Match",required=false) String etag,@RequestHeader(value="Idempotency-Key",required=false) String key,Authentication auth){JoinApplicationView r=service.withdraw(enterpriseId,id,etag,key,rid(),auth);return entity(HttpStatus.OK,r,r.revision()); }
    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "id", style = "requestTextStyle")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/join-applications/{id}/audit") @PreAuthorize("hasAuthority('SCOPE_enterprise.join-application.read')") @RawResponse
    ResponseEntity<ApiResponse<List<InvitationService.AuditEvent>>> appAudit(@PathVariable String enterpriseId,@PathVariable String id,Authentication auth){return ok(service.applicationAudit(enterpriseId,id,auth)); }
    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "search", required = false, style = "requestTextStyle")
    @Field(param = "status", required = false, style = "requestTextStyle")
    @Field(param = "page", required = false, style = "requestIntegerParameterStyle")
    @Field(param = "pageSize", required = false, style = "requestIntegerParameterStyle")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/join-applications") @PreAuthorize("hasAuthority('SCOPE_enterprise.join-application.read')") @RawResponse
    ResponseEntity<ApiResponse<InvitationService.Page<JoinApplicationView>>> applications(@PathVariable String enterpriseId,@RequestParam(required=false) String search,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,Authentication auth){try{return ok(service.listApplications(enterpriseId,search,status,page,pageSize,auth));}catch(Exception e){throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, e.getMessage(), e);}}

    private String rid(){return requestId.currentRequestId();}
    private <T> ResponseEntity<ApiResponse<T>> ok(T data){return ResponseEntity.ok().header("X-Request-Id",rid()).body(ApiResponse.success(data,rid()));}
    private <T> ResponseEntity<ApiResponse<T>> entity(HttpStatus status,T data,String revision){return ResponseEntity.status(status).eTag("\""+revision+"\"").header("X-Request-Id",rid()).body(ApiResponse.success(data,rid()));}
    private <T> ResponseEntity<ApiResponse<T>> entity(HttpStatus status,T data,String revision,HttpServletRequest req){String e="\""+revision+"\"";if(e.equals(req.getHeader(HttpHeaders.IF_NONE_MATCH)))return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(e).header("X-Request-Id",rid()).build();return entity(status,data,revision);}
}
