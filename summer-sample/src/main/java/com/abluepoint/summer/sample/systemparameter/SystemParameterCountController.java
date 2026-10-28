package com.abluepoint.summer.sample.systemparameter;

import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.parameter.api.Field;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import com.abluepoint.summer.mvc.parameter.api.FieldValues;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/system-parameters:count", "/api/v1/enterprises/{enterpriseId}/system-parameters:count"})
class SystemParameterCountController {
    private final SystemParameterQueryService queries;
    private final RequestIdAccessor requestId;

    SystemParameterCountController(SystemParameterQueryService queries, RequestIdAccessor requestId) {
        this.queries = queries;
        this.requestId = requestId;
    }

    @GetMapping
    @RawResponse
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("system.parameter.read")
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.read','SCOPE_platform.parameter.read','SCOPE_customer.read')")
    @Field(param="enterpriseId", required=false, style="systemParameterTenantStyle")
    @Field(param = "page", required = false, style = "systemParameterPageStyle")
    @Field(param = "size", required = false, style = "systemParameterPageSizeStyle")
    @Field(param = "key", required = false, style = "systemParameterKeyStyle")
    @Field(param = "type", required = false, style = "systemParameterTypeStyle")
    @Field(param = "status", required = false, style = "systemParameterStatusStyle")
    @Field(param = "public", required = false, style = "booleanStyle")
    @Field(param = "sort", required = false, style = "systemParameterSortStyle")
    @Field(param = "direction", required = false, style = "systemParameterSortDirectionStyle")
    @Field(param = "projection", required = false, style = "systemParameterProjectionStyle")
    @Field(param = "tenantId", required = false, style = "systemParameterTenantStyle")
    ResponseEntity<ApiResponse<SystemParameterQueryCount>> count(FieldValues values, Authentication authentication) {
        String rid = requestId.currentRequestId();
        try {
            SystemParameterListQuery query = SystemParameterQueryParser.parse(values, authentication);
            return ResponseEntity.ok().header("X-Request-Id", rid)
                    .body(ApiResponse.success(queries.count(query, authentication), rid));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).header("X-Request-Id", rid)
                    .body(ApiResponse.failure(new ApiError("SUMMER_PARAMETER_INVALID", "Invalid system parameter query", List.of()), rid));
        }
    }
}
