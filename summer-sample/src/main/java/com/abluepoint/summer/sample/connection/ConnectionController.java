package com.abluepoint.summer.sample.connection;

import com.abluepoint.summer.mvc.annotation.Field;
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

@RestController @PreAuthorize("isAuthenticated()")
class ConnectionController {
    private final ConnectionService service; private final RequestIdAccessor requestId;
    ConnectionController(ConnectionService s, RequestIdAccessor r){service=s;requestId=r;}
    @GetMapping("/api/v1/platform/connection-services") @RawResponse ResponseEntity<ApiResponse<List<ConnectionServiceView>>> list(Authentication a){return body(HttpStatus.OK,preserveAccessDenied(() -> service.listServices(a)),null);}
    @Field(param = "serviceCode", required = false, nullable = true, style = "requestTextStyle")
    @Field(param = "name", required = false, nullable = true, style = "requestTextStyle")
    @Field(param = "permitAudience", required = false, nullable = true, style = "requestTextStyle")
    @Field(param = "protocolVersion", required = false, nullable = true, style = "requestTextStyle")
    @PostMapping(value="/api/v1/platform/connection-services",consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<ConnectionServiceView>> create(@RequestBody ConnectionServiceCreateRequest r,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){return body(HttpStatus.CREATED,preserveAccessDenied(() -> service.createService(r,k,a)),1L);}
    @Field(param = "serviceId", style = "requestTextStyle")
    @Field(param = "status", style = "requestTextStyle")
    @PatchMapping("/api/v1/platform/connection-services/{serviceId}") @RawResponse ResponseEntity<ApiResponse<ConnectionServiceView>> status(@PathVariable String serviceId,@RequestParam String status,@RequestHeader("If-Match") String ifMatch,@RequestHeader(value="Idempotency-Key",required=false)String key,Authentication a){ConnectionServiceView v=preserveAccessDenied(() -> service.setServiceStatus(serviceId,status,ifMatch,key,a));return body(HttpStatus.OK,v,v.version());}
    @PutMapping(value="/internal/v1/connection-services/{serviceId}/connections/{connectionId}/catalog",consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<McpConnectionView>> catalog(@PathVariable String serviceId,@PathVariable String connectionId,@RequestBody CatalogSyncRequest r,Authentication a){McpConnectionView v=preserveAccessDenied(() -> service.syncCatalog(serviceId,connectionId,r,a));return body(HttpStatus.OK,v,v.version());}
    @Field(param = "enterpriseId", style = "requestTextStyle")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/mcp-connections") @RawResponse ResponseEntity<ApiResponse<List<McpConnectionView>>> bindings(@PathVariable String enterpriseId,Authentication a){return body(HttpStatus.OK,preserveAccessDenied(() -> service.listBindings(enterpriseId,a)),null);}
    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "connectionServiceId", required = false, nullable = true, style = "requestTextStyle")
    @Field(param = "externalConnectionId", required = false, nullable = true, style = "requestTextStyle")
    @PostMapping(value="/api/v1/enterprises/{enterpriseId}/mcp-connections", consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<McpConnectionView>> createBinding(@PathVariable String enterpriseId,@RequestBody McpConnectionCreateRequest r,@RequestHeader(value="Idempotency-Key",required=false) String key,Authentication a){McpConnectionView v=preserveAccessDenied(() -> service.createBinding(enterpriseId,r,key,a));return body(HttpStatus.CREATED,v,v.version());}
    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "connectionId", style = "requestTextStyle")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/mcp-connections/{connectionId}") @RawResponse ResponseEntity<ApiResponse<McpConnectionView>> binding(@PathVariable String enterpriseId,@PathVariable String connectionId,Authentication a){McpConnectionView v=preserveAccessDenied(() -> service.binding(enterpriseId,connectionId,a));return body(HttpStatus.OK,v,v.version());}
    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "connectionId", style = "requestTextStyle")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/mcp-connections/{connectionId}/tools") @RawResponse ResponseEntity<ApiResponse<List<McpToolView>>> tools(@PathVariable String enterpriseId,@PathVariable String connectionId,Authentication a){return body(HttpStatus.OK,preserveAccessDenied(() -> service.tools(enterpriseId,connectionId,a)),null);}
    @Field(param = "enterpriseId", style = "requestTextStyle")
    @Field(param = "connectionId", style = "requestTextStyle")
    @GetMapping("/api/v1/enterprises/{enterpriseId}/mcp-connections/{connectionId}/health") @RawResponse ResponseEntity<ApiResponse<McpConnectionHealthView>> health(@PathVariable String enterpriseId,@PathVariable String connectionId,Authentication a){return body(HttpStatus.OK,preserveAccessDenied(() -> service.health(enterpriseId,connectionId,a)),null);}
    private String rid(){return requestId.currentRequestId();}
    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus s,T v,Long ver){ResponseEntity.BodyBuilder b=ResponseEntity.status(s).header("X-Request-Id",rid());if(ver!=null)b.eTag("\""+ver+"\"");return b.body(ApiResponse.success(v,rid()));}

    /** Retains this application endpoint's original safe security message and native cause. */
    private <T> T preserveAccessDenied(Supplier<T> action) {
        try {
            return action.get();
        } catch (AccessDeniedException denied) {
            throw SummerException.withMessage(CommonErrorCode.FORBIDDEN, denied.getMessage(), denied);
        }
    }
}
