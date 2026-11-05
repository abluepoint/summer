package com.abluepoint.summer.sample.application;
import java.util.function.Supplier;
import org.springframework.security.access.AccessDeniedException;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.error.CommonErrorCode;

import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@PreAuthorize("isAuthenticated()")
class ApplicationController {
 private final ApplicationService service; private final RequestIdAccessor requestId;
 ApplicationController(ApplicationService s,RequestIdAccessor r){service=s;requestId=r;}
 @GetMapping("/api/v1/enterprises/{enterpriseId}/applications") @PreAuthorize("@businessMethodAccess.require(authentication, 'enterprise.application.read', 'enterprise.applications.read') or (hasAnyAuthority('SCOPE_enterprise.application.read','SCOPE_enterprise.applications.read'))") @RawResponse ResponseEntity<ApiResponse<List<ApplicationView>>> list(@PathVariable String enterpriseId,Authentication a){return ok(service.list(enterpriseId,a),null);}
 @PostMapping(value="/api/v1/enterprises/{enterpriseId}/applications",consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<ApplicationView>> create(@PathVariable String enterpriseId,@RequestBody ApplicationCreateRequest b,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){ApplicationView v=preserveAccessDenied(() -> service.create(enterpriseId,b,k,rid(),a));return out(HttpStatus.CREATED,v,v.version());}
 @GetMapping("/api/v1/enterprises/{enterpriseId}/applications/{id}") @RawResponse ResponseEntity<ApiResponse<ApplicationView>> detail(@PathVariable String enterpriseId,@PathVariable String id,Authentication a){return out(HttpStatus.OK,preserveAccessDenied(() -> service.detail(enterpriseId,id,a)),preserveAccessDenied(() -> service.detail(enterpriseId,id,a)).version());}
 @PatchMapping(value="/api/v1/enterprises/{enterpriseId}/applications/{id}",consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<ApplicationView>> update(@PathVariable String enterpriseId,@PathVariable String id,@RequestBody ApplicationUpdateRequest b,@RequestHeader("If-Match")String ifm,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){ApplicationView v=preserveAccessDenied(() -> service.update(enterpriseId,id,b,ifm,k,rid(),a));return out(HttpStatus.OK,v,v.version());}
 @PostMapping("/api/v1/enterprises/{enterpriseId}/applications/{id}:enable") @RawResponse ResponseEntity<ApiResponse<ApplicationView>> enable(@PathVariable String enterpriseId,@PathVariable String id,@RequestHeader("If-Match")String ifm,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){return status(enterpriseId,id,"ACTIVE",ifm,k,a);}
 @PostMapping("/api/v1/enterprises/{enterpriseId}/applications/{id}:disable") @RawResponse ResponseEntity<ApiResponse<ApplicationView>> disable(@PathVariable String enterpriseId,@PathVariable String id,@RequestHeader("If-Match")String ifm,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){return status(enterpriseId,id,"DISABLED",ifm,k,a);}
 private ResponseEntity<ApiResponse<ApplicationView>> status(String e,String id,String st,String ifm,String k,Authentication a){ApplicationView v=preserveAccessDenied(() -> service.setStatus(e,id,st,ifm,k,rid(),a));return out(HttpStatus.OK,v,v.version());}
 @GetMapping("/api/v1/enterprises/{enterpriseId}/applications/{id}/oauth-config") @RawResponse ResponseEntity<ApiResponse<OAuthConfigView>> oauth(@PathVariable String enterpriseId,@PathVariable String id,Authentication a){ApplicationView v=preserveAccessDenied(() -> service.detail(enterpriseId,id,a));return out(HttpStatus.OK,v.oauthConfig(),v.oauthConfig().version());}
 @PutMapping(value="/api/v1/enterprises/{enterpriseId}/applications/{id}/oauth-config",consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<OAuthConfigView>> config(@PathVariable String enterpriseId,@PathVariable String id,@RequestBody OAuthConfigRequest b,@RequestHeader("If-Match")String ifm,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){ApplicationView v=preserveAccessDenied(() -> service.configure(enterpriseId,id,b,ifm,k,rid(),a));return out(HttpStatus.OK,v.oauthConfig(),v.version());}
 @GetMapping("/api/v1/enterprises/{enterpriseId}/applications/{id}/credentials") @RawResponse ResponseEntity<ApiResponse<List<CredentialView>>> credentials(@PathVariable String enterpriseId,@PathVariable String id,Authentication a){return ok(preserveAccessDenied(() -> service.credentials(enterpriseId,id,a)),null);}
 @PostMapping("/api/v1/enterprises/{enterpriseId}/applications/{id}/credentials:rotate") @RawResponse ResponseEntity<ApiResponse<CredentialView>> rotate(@PathVariable String enterpriseId,@PathVariable String id,@RequestHeader("If-Match")String ifm,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){CredentialView v=preserveAccessDenied(() -> service.rotate(enterpriseId,id,ifm,k,rid(),a));return out(HttpStatus.OK,v,(long)v.version());}
 @PostMapping("/api/v1/enterprises/{enterpriseId}/applications/{id}/credentials/{cid}:revoke") @RawResponse ResponseEntity<ApiResponse<CredentialView>> revoke(@PathVariable String enterpriseId,@PathVariable String id,@PathVariable String cid,@RequestHeader("If-Match")String ifm,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){return out(HttpStatus.OK,preserveAccessDenied(() -> service.revoke(enterpriseId,id,cid,ifm,k,rid(),a)),null);}
 @GetMapping("/api/v1/enterprises/{enterpriseId}/applications/{id}/audit") @RawResponse ResponseEntity<ApiResponse<List<java.util.Map<String,Object>>>> audit(@PathVariable String enterpriseId,@PathVariable String id,Authentication a){return ok(preserveAccessDenied(() -> service.audit(enterpriseId,id,a)),null);}
 private String rid(){return requestId.currentRequestId();}
 private <T> ResponseEntity<ApiResponse<T>> ok(T b,Long v){return out(HttpStatus.OK,b,v);}
 private <T> ResponseEntity<ApiResponse<T>> out(HttpStatus s,T b,Long ver){ResponseEntity.BodyBuilder rb=ResponseEntity.status(s).header("X-Request-Id",rid());if(ver!=null)rb.eTag("\""+ver+"\"");return rb.body(ApiResponse.success(b,rid()));}

    /** Retains this application endpoint's original safe security message and native cause. */
    private <T> T preserveAccessDenied(Supplier<T> action) {
        try {
            return action.get();
        } catch (AccessDeniedException denied) {
            throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, denied.getMessage(), denied);
        }
    }
}
