package com.abluepoint.summer.sample.systemparameter;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.response.ApiResponse;
import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.mvc.api.RawResponse;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.mvc.validate.FieldValues;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping({"/api/system-parameters", "/api/v1/enterprises/{enterpriseId}/system-parameters"})
class SystemParameterController {
    private final SystemParameterService service;
    private final SystemParameterReleaseService releases;
    private final SystemParameterQueryService queryService;
    private final RequestIdAccessor requestId;

    SystemParameterController(SystemParameterService service, SystemParameterReleaseService releases,
                              SystemParameterQueryService queryService, RequestIdAccessor requestId) {
        this.service = service;
        this.releases = releases;
        this.queryService = queryService;
        this.requestId = requestId;
    }

    @GetMapping
    @RawResponse
    @com.abluepoint.summer.sample.resource.ResourcePermission("system.parameter.read")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'system.parameter.read', 'platform.parameter.read', 'customer.read') or (hasAnyAuthority('SCOPE_system.parameter.read','SCOPE_platform.parameter.read','SCOPE_customer.read'))")
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
    ResponseEntity<ApiResponse<Map<String, Object>>> list(
            FieldValues values, Authentication authentication) {
        try {
            SystemParameterListQuery query = SystemParameterQueryParser.parse(values, authentication);
            return body(HttpStatus.OK, page(queryService.list(query, authentication)));
        } catch (IllegalArgumentException exception) {
            throw SummerException.withMessage(SystemParameterErrorCode.INVALID, "Invalid system parameter query", exception);
        }
    }

    @GetMapping("/{key}")
    @RawResponse
    @com.abluepoint.summer.sample.resource.ResourcePermission("system.parameter.read")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'system.parameter.read', 'platform.parameter.read', 'customer.read') or (hasAnyAuthority('SCOPE_system.parameter.read','SCOPE_platform.parameter.read','SCOPE_customer.read'))")
    @Field(param="enterpriseId", required=false, style="systemParameterTenantStyle")
    @Field(param = "key", style = "systemParameterKeyStyle")
    ResponseEntity<ApiResponse<Map<String, Object>>> find(
            @PathVariable String key, Authentication authentication) {
        return body(HttpStatus.OK, parameter(service.find(key, authentication)));
    }

    @PostMapping
    @RawResponse
    @com.abluepoint.summer.sample.resource.ResourcePermission("system.parameter.create")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'system.parameter.create', 'platform.parameter.create', 'system.parameter.write', 'platform.parameter.write') or (hasAnyAuthority('SCOPE_system.parameter.create','SCOPE_platform.parameter.create','SCOPE_system.parameter.write','SCOPE_platform.parameter.write'))")
    ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @RequestBody CreateSystemParameterRequest request,
            Authentication authentication) {
        return body(HttpStatus.CREATED, parameter(service.create(request, rid(), authentication)));
    }

    @PatchMapping("/{key}")
    @RawResponse
    @com.abluepoint.summer.sample.resource.ResourcePermission("system.parameter.update")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'system.parameter.update', 'platform.parameter.update', 'system.parameter.write', 'platform.parameter.write') or (hasAnyAuthority('SCOPE_system.parameter.update','SCOPE_platform.parameter.update','SCOPE_system.parameter.write','SCOPE_platform.parameter.write'))")
    ResponseEntity<ApiResponse<Map<String, Object>>> update(
            @PathVariable String key,
            @RequestBody UpdateSystemParameterRequest request,
            Authentication authentication) {
        return body(HttpStatus.OK, parameter(service.update(key, request, rid(), authentication)));
    }

    @PostMapping("/{key}/enable")
    @RawResponse
    @com.abluepoint.summer.sample.resource.ResourcePermission("system.parameter.publish")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'system.parameter.publish', 'platform.parameter.publish', 'system.parameter.write', 'platform.parameter.write') or (hasAnyAuthority('SCOPE_system.parameter.publish','SCOPE_platform.parameter.publish','SCOPE_system.parameter.write','SCOPE_platform.parameter.write'))")
    @Field(param="enterpriseId", required=false, style="systemParameterTenantStyle")
    @Field(param = "key", style = "systemParameterKeyStyle")
    ResponseEntity<ApiResponse<Map<String, Object>>> enable(
            @PathVariable String key,
            Authentication authentication) {
        return status(key, SystemParameterStatus.ACTIVE, authentication);
    }

    @PostMapping("/{key}/disable")
    @RawResponse
    @com.abluepoint.summer.sample.resource.ResourcePermission("system.parameter.disable")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'system.parameter.disable', 'platform.parameter.disable', 'system.parameter.write', 'platform.parameter.write') or (hasAnyAuthority('SCOPE_system.parameter.disable','SCOPE_platform.parameter.disable','SCOPE_system.parameter.write','SCOPE_platform.parameter.write'))")
    @Field(param="enterpriseId", required=false, style="systemParameterTenantStyle")
    @Field(param = "key", style = "systemParameterKeyStyle")
    ResponseEntity<ApiResponse<Map<String, Object>>> disable(
            @PathVariable String key,
            Authentication authentication) {
        return status(key, SystemParameterStatus.INACTIVE, authentication);
    }

    @PostMapping("/{key}/draft")
    @RawResponse
    @com.abluepoint.summer.sample.resource.ResourcePermission("system.parameter.update")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'system.parameter.update', 'platform.parameter.update', 'system.parameter.write', 'platform.parameter.write') or (hasAnyAuthority('SCOPE_system.parameter.update','SCOPE_platform.parameter.update','SCOPE_system.parameter.write','SCOPE_platform.parameter.write'))")
    ResponseEntity<ApiResponse<Map<String, Object>>> draft(@PathVariable String key,
                                                            @RequestBody SystemParameterDraftRequest request,
                                                            Authentication authentication) {
        return body(HttpStatus.CREATED, release(releases.draft(key, request, rid(), authentication)));
    }

    @GetMapping("/{key}/versions")
    @RawResponse
    @com.abluepoint.summer.sample.resource.ResourcePermission("system.parameter.read")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'system.parameter.read', 'platform.parameter.read', 'customer.read') or (hasAnyAuthority('SCOPE_system.parameter.read','SCOPE_platform.parameter.read','SCOPE_customer.read'))")
    ResponseEntity<ApiResponse<Map<String, Object>>> versions(@PathVariable String key, Authentication authentication) {
        return body(HttpStatus.OK, versionPage(releases.versions(key, authentication)));
    }

    @PostMapping("/{key}/publish")
    @RawResponse
    @com.abluepoint.summer.sample.resource.ResourcePermission("system.parameter.publish")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'system.parameter.publish', 'platform.parameter.publish', 'system.parameter.write', 'platform.parameter.write') or (hasAnyAuthority('SCOPE_system.parameter.publish','SCOPE_platform.parameter.publish','SCOPE_system.parameter.write','SCOPE_platform.parameter.write'))")
    ResponseEntity<ApiResponse<Map<String, Object>>> publish(@PathVariable String key,
                                                               @RequestBody SystemParameterReleaseCommand command,
                                                               Authentication authentication) {
        return body(HttpStatus.OK, release(releases.publish(key, command, rid(), authentication)));
    }

    @PostMapping("/{key}/rollback")
    @RawResponse
    @com.abluepoint.summer.sample.resource.ResourcePermission("system.parameter.rollback")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'system.parameter.rollback', 'platform.parameter.rollback', 'system.parameter.write', 'platform.parameter.write') or (hasAnyAuthority('SCOPE_system.parameter.rollback','SCOPE_platform.parameter.rollback','SCOPE_system.parameter.write','SCOPE_platform.parameter.write'))")
    ResponseEntity<ApiResponse<Map<String, Object>>> rollback(@PathVariable String key,
                                                                @RequestBody SystemParameterReleaseCommand command,
                                                                Authentication authentication) {
        return body(HttpStatus.OK, release(releases.rollback(key, command, rid(), authentication)));
    }

    @PostMapping("/{key}/deactivate")
    @RawResponse
    @com.abluepoint.summer.sample.resource.ResourcePermission("system.parameter.disable")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'system.parameter.disable', 'platform.parameter.disable', 'system.parameter.write', 'platform.parameter.write') or (hasAnyAuthority('SCOPE_system.parameter.disable','SCOPE_platform.parameter.disable','SCOPE_system.parameter.write','SCOPE_platform.parameter.write'))")
    ResponseEntity<ApiResponse<Map<String, Object>>> deactivate(@PathVariable String key,
                                                                  @RequestBody SystemParameterReleaseCommand command,
                                                                  Authentication authentication) {
        return body(HttpStatus.OK, release(releases.deactivate(key, command, rid(), authentication)));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> status(
            String key, SystemParameterStatus status, Authentication authentication) {
        return body(HttpStatus.OK, parameter(service.setStatus(key, status, rid(), authentication)));
    }

    @PutMapping("/{key}/overrides/{organization}")
    @RawResponse
    @com.abluepoint.summer.sample.resource.ResourcePermission("system.parameter.publish")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'system.parameter.publish', 'system.parameter.write', 'platform.parameter.write') or (hasAnyAuthority('SCOPE_system.parameter.publish','SCOPE_system.parameter.write','SCOPE_platform.parameter.write'))")
    ResponseEntity<ApiResponse<com.abluepoint.summer.sample.configuration.ConfigurationOverrideView>> override(
            @PathVariable String key, @PathVariable String organization,
            @RequestBody com.abluepoint.summer.sample.configuration.ConfigurationOverrideRequest request, Authentication auth) {
        return body(HttpStatus.OK, service.override(key, organization, request, auth, rid()));
    }

    private String rid() {
        return requestId.currentRequestId();
    }

    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus status, T value) {
        return ResponseEntity.status(status).header("X-Request-Id", rid())
                .body(ApiResponse.success(value, rid()));
    }

    private static Map<String, Object> page(SystemParameterQueryPage page) {
        return Map.of(
                "items", page.items().stream().map(SystemParameterController::item).toList(),
                "page", page.page(),
                "size", page.size(),
                "totalElements", page.totalElements(),
                "totalPages", page.totalPages(),
                "projection", page.projection());
    }

    private static Map<String, Object> item(Object value) {
        if (value instanceof SystemParameterSummaryView summary) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", summary.id());
            result.put("key", summary.key());
            result.put("type", summary.type());
            result.put("status", summary.status());
            result.put("public", summary.publicValue());
            return result;
        }
        return parameter((SystemParameterView) value);
    }

    private static Map<String, Object> parameter(SystemParameterView value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.id());
        result.put("key", value.key());
        result.put("type", value.type());
        result.put("value", value.value());
        result.put("required", value.required());
        result.put("min", value.min());
        result.put("max", value.max());
        result.put("pattern", value.pattern());
        result.put("public", value.publicValue());
        result.put("description", value.description());
        result.put("status", value.status());
        result.put("version", value.version());
        result.put("tenantId", value.tenantId());
        result.put("createdAt", value.createdAt());
        result.put("updatedAt", value.updatedAt());
        return result;
    }

    private static Map<String, Object> release(SystemParameterReleaseResult value) {
        Map<String, Object> result = parameter(value.parameter());
        result.put("revision", value.revision());
        result.put("state", value.state());
        return result;
    }

    private static Map<String, Object> versionPage(SystemParameterVersionPage value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", value.items().stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("revision", item.revision()); row.put("state", item.state());
            row.put("parameterKey", item.parameterKey()); row.put("baseVersion", item.baseVersion());
            row.put("principal", item.principal()); row.put("createdAt", item.createdAt());
            row.put("value", item.value()); row.put("status", item.status());
            row.put("currentVersion", item.currentVersion()); return row;
        }).toList());
        result.put("currentVersion", value.currentVersion());
        result.put("currentState", value.currentState());
        return result;
    }
}
