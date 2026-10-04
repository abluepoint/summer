package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.common.response.ApiError;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
class DataScopeController {
    private final DataScopeService service; private final RequestIdAccessor requestId;
    DataScopeController(DataScopeService service, RequestIdAccessor requestId){this.service=service;this.requestId=requestId;}
    @GetMapping("/api/v1/enterprises/{enterpriseId}/data-scopes") @RawResponse ResponseEntity<ApiResponse<List<DataScopePolicyView>>> list(@PathVariable String enterpriseId, Authentication a){try{return ok(service.list(enterpriseId,a));}catch(Exception e){return error(e);}}
    @GetMapping("/api/v1/enterprises/{enterpriseId}/data-scopes/{resource}") @RawResponse ResponseEntity<ApiResponse<DataScopePolicyView>> get(@PathVariable String enterpriseId,@PathVariable String resource,Authentication a){try{return ok(service.get(enterpriseId,resource,a));}catch(Exception e){return error(e);}}
    @PutMapping(value="/api/v1/enterprises/{enterpriseId}/data-scopes/{resource}", consumes=MediaType.APPLICATION_JSON_VALUE) @RawResponse ResponseEntity<ApiResponse<DataScopePolicyView>> put(@PathVariable String enterpriseId,@PathVariable String resource,@RequestBody DataScopePolicyRequest body,Authentication a){try{return ok(service.put(enterpriseId,resource,body,a));}catch(Exception e){return error(e);}}
    private <T> ResponseEntity<ApiResponse<T>> ok(T body){return ResponseEntity.ok().header("X-Request-Id",requestId.currentRequestId()).body(ApiResponse.success(body,requestId.currentRequestId()));}
    private <T> ResponseEntity<ApiResponse<T>> error(Exception e){HttpStatus s=HttpStatus.INTERNAL_SERVER_ERROR;String c="SUMMER_INTERNAL_ERROR";if(e instanceof AccessDeniedException){s=HttpStatus.FORBIDDEN;c="SUMMER_FORBIDDEN";}else if(e instanceof IllegalArgumentException){s=HttpStatus.BAD_REQUEST;c="SUMMER_DATA_SCOPE_INVALID";}else if(e instanceof NoSuchElementException){s=HttpStatus.NOT_FOUND;c="SUMMER_NOT_FOUND";}return ResponseEntity.status(s).header("X-Request-Id",requestId.currentRequestId()).body(ApiResponse.failure(new ApiError(c,e.getMessage(),List.of()),requestId.currentRequestId()));}
}
