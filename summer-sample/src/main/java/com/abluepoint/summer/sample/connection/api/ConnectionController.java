package com.abluepoint.summer.sample.connection.api;
import com.abluepoint.summer.sample.connection.domain.ConnectionValidationException;
import com.abluepoint.summer.sample.connection.application.ConnectionService;
import com.abluepoint.summer.sample.connection.application.McpToolView;
import com.abluepoint.summer.sample.connection.domain.ConnectionIdempotencyConflictException;
import com.abluepoint.summer.sample.connection.application.CatalogSyncCommand;
import com.abluepoint.summer.sample.connection.application.McpConnectionHealthView;
import com.abluepoint.summer.sample.connection.domain.ConnectionPreconditionException;
import com.abluepoint.summer.sample.connection.application.ConnectionServiceCreateCommand;
import com.abluepoint.summer.sample.connection.application.ConnectionServiceView;
import com.abluepoint.summer.sample.connection.application.McpConnectionCreateCommand;
import com.abluepoint.summer.sample.connection.domain.ConnectionConflictException;
import com.abluepoint.summer.sample.connection.application.McpConnectionView;

import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @PreAuthorize("isAuthenticated()")
public class ConnectionController {
    private final ConnectionService service; private final RequestIdAccessor requestId;
    ConnectionController(ConnectionService s, RequestIdAccessor r){service=s;requestId=r;}
    @GetMapping("/api/v1/platform/connection-services") @RawResponse ResponseEntity<ApiResponse<List<ConnectionServiceView>>> list(Authentication a){try{return body(HttpStatus.OK,service.listServices(a),null);}catch(Exception e){return error(e);}}
    @PostMapping(value="/api/v1/platform/connection-services",consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<ConnectionServiceView>> create(@RequestBody ConnectionServiceCreateRequest r,@RequestHeader(value="Idempotency-Key",required=false)String k,Authentication a){try{return body(HttpStatus.CREATED,service.createService(r.toCommand(),k,a),1L);}catch(Exception e){return error(e);}}
    @PatchMapping("/api/v1/platform/connection-services/{serviceId}") @RawResponse ResponseEntity<ApiResponse<ConnectionServiceView>> status(@PathVariable String serviceId,@RequestParam String status,@RequestHeader("If-Match") String ifMatch,@RequestHeader(value="Idempotency-Key",required=false)String key,Authentication a){try{ConnectionServiceView v=service.setServiceStatus(serviceId,status,ifMatch,key,a);return body(HttpStatus.OK,v,v.version());}catch(Exception e){return error(e);}}
    @PutMapping(value="/internal/v1/connection-services/{serviceId}/connections/{connectionId}/catalog",consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<McpConnectionView>> catalog(@PathVariable String serviceId,@PathVariable String connectionId,@RequestBody CatalogSyncRequest r,Authentication a){try{McpConnectionView v=service.syncCatalog(serviceId,connectionId,r.toCommand(),a);return body(HttpStatus.OK,v,v.version());}catch(Exception e){return error(e);}}
    @GetMapping("/api/v1/enterprises/{enterpriseId}/mcp-connections") @RawResponse ResponseEntity<ApiResponse<List<McpConnectionView>>> bindings(@PathVariable String enterpriseId,Authentication a){try{return body(HttpStatus.OK,service.listBindings(enterpriseId,a),null);}catch(Exception e){return error(e);}}
    @PostMapping(value="/api/v1/enterprises/{enterpriseId}/mcp-connections", consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<McpConnectionView>> createBinding(@PathVariable String enterpriseId,@RequestBody McpConnectionCreateRequest r,@RequestHeader(value="Idempotency-Key",required=false) String key,Authentication a){try{McpConnectionView v=service.createBinding(enterpriseId,r.toCommand(),key,a);return body(HttpStatus.CREATED,v,v.version());}catch(Exception e){return error(e);}}
    @GetMapping("/api/v1/enterprises/{enterpriseId}/mcp-connections/{connectionId}") @RawResponse ResponseEntity<ApiResponse<McpConnectionView>> binding(@PathVariable String enterpriseId,@PathVariable String connectionId,Authentication a){try{McpConnectionView v=service.binding(enterpriseId,connectionId,a);return body(HttpStatus.OK,v,v.version());}catch(Exception e){return error(e);}}
    @GetMapping("/api/v1/enterprises/{enterpriseId}/mcp-connections/{connectionId}/tools") @RawResponse ResponseEntity<ApiResponse<List<McpToolView>>> tools(@PathVariable String enterpriseId,@PathVariable String connectionId,Authentication a){try{return body(HttpStatus.OK,service.tools(enterpriseId,connectionId,a),null);}catch(Exception e){return error(e);}}
    @GetMapping("/api/v1/enterprises/{enterpriseId}/mcp-connections/{connectionId}/health") @RawResponse ResponseEntity<ApiResponse<McpConnectionHealthView>> health(@PathVariable String enterpriseId,@PathVariable String connectionId,Authentication a){try{return body(HttpStatus.OK,service.health(enterpriseId,connectionId,a),null);}catch(Exception e){return error(e);}}
    private String rid(){return requestId.currentRequestId();}
    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus s,T v,Long ver){ResponseEntity.BodyBuilder b=ResponseEntity.status(s).header("X-Request-Id",rid());if(ver!=null)b.eTag("\""+ver+"\"");return b.body(ApiResponse.success(v,rid()));}
    private <T> ResponseEntity<ApiResponse<T>> error(Exception e){HttpStatus s=HttpStatus.INTERNAL_SERVER_ERROR;String c="SUMMER_INTERNAL_ERROR";if(e instanceof org.springframework.security.access.AccessDeniedException){s=HttpStatus.FORBIDDEN;c="SUMMER_FORBIDDEN";}else if(e instanceof ConnectionValidationException){s=HttpStatus.BAD_REQUEST;c="SUMMER_CONNECTION_INVALID";}else if(e instanceof ConnectionPreconditionException){s=HttpStatus.PRECONDITION_FAILED;c="SUMMER_PRECONDITION_FAILED";}else if(e instanceof ConnectionConflictException||e instanceof ConnectionIdempotencyConflictException){s=HttpStatus.CONFLICT;c=e instanceof ConnectionIdempotencyConflictException?"SUMMER_IDEMPOTENCY_CONFLICT":"SUMMER_CONNECTION_CONFLICT";}return ResponseEntity.status(s).header("X-Request-Id",rid()).body(ApiResponse.failure(new ApiError(c,e.getMessage(),List.of()),rid()));}
}
