package com.abluepoint.summer.sample.physicalconnection;

import com.abluepoint.summer.mvc.annotation.Field;
import java.util.function.Supplier;
import org.springframework.security.access.AccessDeniedException;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.response.*;import com.abluepoint.summer.mvc.api.RawResponse;import com.abluepoint.summer.mvc.request.RequestIdAccessor;import org.springframework.http.*;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController @PreAuthorize("isAuthenticated()") class PhysicalExecutionController{final PhysicalExecutionService s;final RequestIdAccessor r;PhysicalExecutionController(PhysicalExecutionService s,RequestIdAccessor r){this.s=s;this.r=r;}
    @Field(param = "connectionId", style = "requestTextStyle")
    @Field(param = "tool", style = "requestTextStyle")
    @Field(param = "permitId", required = false, nullable = true, style = "requestTextStyle")
    @Field(param = "executionAttemptId", required = false, nullable = true, style = "requestTextStyle")
    @Field(param = "schemaDigest", required = false, nullable = true, style = "requestTextStyle")
    @Field(param = "argumentsDigest", required = false, nullable = true, style = "requestTextStyle")
    @Field(param = "sessionRef", required = false, nullable = true, style = "requestTextStyle")
    @Field(param = "timeoutMs", required = false, nullable = true, style = "requestLongStyle")
    @Field(param = "maxRetries", required = false, nullable = true, style = "requestIntegerStyle")
    @Field(param = "mcpAccessToken", required = false, nullable = true, style = "requestTextStyle")
    @PostMapping(value="/internal/v1/connections/{connectionId}/tools/{tool}:invoke",consumes=MediaType.APPLICATION_JSON_VALUE)@RawResponse ResponseEntity<ApiResponse<PhysicalExecutionView>> invoke(@PathVariable String connectionId,@PathVariable String tool,@RequestBody PhysicalInvokeRequest q,Authentication a){return body(HttpStatus.OK,preserveAccessDenied(() -> s.invoke(connectionId,tool,a instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken j?j.getToken().getClaimAsString("connection_service_id"):null,q,a)));}private<T>ResponseEntity<ApiResponse<T>>body(HttpStatus h,T v){return ResponseEntity.status(h).header("X-Request-Id",r.currentRequestId()).body(ApiResponse.success(v,r.currentRequestId()));}

    /** Retains this application endpoint's original safe security message and native cause. */
    private <T> T preserveAccessDenied(Supplier<T> action) {
        try {
            return action.get();
        } catch (AccessDeniedException denied) {
            throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, denied.getMessage(), denied);
        }
    }
}
