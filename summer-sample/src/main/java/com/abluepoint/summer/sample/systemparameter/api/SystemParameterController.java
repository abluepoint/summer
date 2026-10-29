package com.abluepoint.summer.sample.systemparameter.api;

import com.abluepoint.summer.sample.systemparameter.api.request.SystemParameterCreateRequest;
import com.abluepoint.summer.sample.systemparameter.api.request.SystemParameterDraftRequest;
import com.abluepoint.summer.sample.systemparameter.api.request.SystemParameterListRequest;
import com.abluepoint.summer.sample.systemparameter.api.request.SystemParameterReleaseRequest;
import com.abluepoint.summer.sample.systemparameter.api.request.SystemParameterUpdateRequest;
import com.abluepoint.summer.sample.systemparameter.application.SystemParameterQueryService;
import com.abluepoint.summer.sample.systemparameter.application.SystemParameterReleaseService;
import com.abluepoint.summer.sample.systemparameter.application.SystemParameterService;
import com.abluepoint.summer.sample.systemparameter.application.view.SystemParameterQueryPageView;
import com.abluepoint.summer.sample.systemparameter.application.view.SystemParameterReleaseView;
import com.abluepoint.summer.sample.systemparameter.application.view.SystemParameterSummaryView;
import com.abluepoint.summer.sample.systemparameter.application.view.SystemParameterVersionPageView;
import com.abluepoint.summer.sample.systemparameter.application.view.SystemParameterView;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterConflictException;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterNotFoundException;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterReleaseConflictException;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterReleaseNotFoundException;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterReleaseValidationException;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterStatus;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterValidationException;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterVersionConflictException;

import com.abluepoint.summer.sample.configuration.api.request.ConfigurationOverrideRequest;
import com.abluepoint.summer.sample.configuration.application.view.ConfigurationOverrideView;

import com.abluepoint.summer.common.error.api.SummerException;
import com.abluepoint.summer.common.response.api.ApiError;
import com.abluepoint.summer.common.response.api.ApiResponse;
import com.abluepoint.summer.mvc.parameter.api.Field;
import com.abluepoint.summer.mvc.response.api.RawResponse;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import com.abluepoint.summer.mvc.parameter.api.FieldValues;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/system-parameters", "/api/v1/enterprises/{enterpriseId}/system-parameters"})
public class SystemParameterController {
    private final SystemParameterService service;
    private final SystemParameterReleaseService releases;
    private final SystemParameterQueryService queryService;
    private final RequestIdAccessor requestId;

    public SystemParameterController(SystemParameterService service, SystemParameterReleaseService releases,
                              SystemParameterQueryService queryService, RequestIdAccessor requestId) {
        this.service = service;
        this.releases = releases;
        this.queryService = queryService;
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
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            FieldValues values, Authentication authentication) {
        try {
            SystemParameterListRequest query = SystemParameterQueryParser.parse(values, authentication);
            return body(HttpStatus.OK, page(queryService.list(query, authentication)));
        } catch (IllegalArgumentException exception) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_PARAMETER_INVALID", "Invalid system parameter query", List.of());
        }
    }

    @GetMapping("/{key}")
    @RawResponse
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("system.parameter.read")
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.read','SCOPE_platform.parameter.read','SCOPE_customer.read')")
    @Field(param="enterpriseId", required=false, style="systemParameterTenantStyle")
    @Field(param = "key", style = "systemParameterKeyStyle")
    public ResponseEntity<ApiResponse<Map<String, Object>>> find(
            @PathVariable String key, Authentication authentication) {
        try {
            return body(HttpStatus.OK, parameter(service.find(key, authentication)));
        } catch (SystemParameterNotFoundException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", exception.getMessage(), List.of());
        }
    }

    @PostMapping
    @RawResponse
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("system.parameter.create")
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.create','SCOPE_platform.parameter.create','SCOPE_system.parameter.write','SCOPE_platform.parameter.write')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @RequestBody SystemParameterCreateRequest request,
            Authentication authentication) {
        try {
            return body(HttpStatus.CREATED, parameter(service.create(request.toCommand(), rid(), authentication)));
        } catch (SystemParameterValidationException exception) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_PARAMETER_INVALID",
                    exception.getMessage(), violations(exception.violations()));
        } catch (SystemParameterConflictException exception) {
            return failure(HttpStatus.CONFLICT, "SUMMER_PARAMETER_CONFLICT",
                    exception.getMessage(), List.of());
        }
    }

    @PatchMapping("/{key}")
    @RawResponse
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("system.parameter.update")
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.update','SCOPE_platform.parameter.update','SCOPE_system.parameter.write','SCOPE_platform.parameter.write')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(
            @PathVariable String key,
            @RequestBody SystemParameterUpdateRequest request,
            Authentication authentication) {
        try {
            return body(HttpStatus.OK, parameter(service.update(key, request.toCommand(), rid(), authentication)));
        } catch (SystemParameterValidationException exception) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_PARAMETER_INVALID",
                    exception.getMessage(), violations(exception.violations()));
        } catch (SystemParameterVersionConflictException exception) {
            return failure(HttpStatus.CONFLICT, "SUMMER_PARAMETER_VERSION_CONFLICT",
                    exception.getMessage(), List.of());
        } catch (SystemParameterNotFoundException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", exception.getMessage(), List.of());
        }
    }

    @PostMapping("/{key}/enable")
    @RawResponse
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("system.parameter.publish")
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.publish','SCOPE_platform.parameter.publish','SCOPE_system.parameter.write','SCOPE_platform.parameter.write')")
    @Field(param="enterpriseId", required=false, style="systemParameterTenantStyle")
    @Field(param = "key", style = "systemParameterKeyStyle")
    public ResponseEntity<ApiResponse<Map<String, Object>>> enable(
            @PathVariable String key,
            Authentication authentication) {
        return status(key, SystemParameterStatus.ACTIVE, authentication);
    }

    @PostMapping("/{key}/disable")
    @RawResponse
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("system.parameter.disable")
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.disable','SCOPE_platform.parameter.disable','SCOPE_system.parameter.write','SCOPE_platform.parameter.write')")
    @Field(param="enterpriseId", required=false, style="systemParameterTenantStyle")
    @Field(param = "key", style = "systemParameterKeyStyle")
    public ResponseEntity<ApiResponse<Map<String, Object>>> disable(
            @PathVariable String key,
            Authentication authentication) {
        return status(key, SystemParameterStatus.INACTIVE, authentication);
    }

    @PostMapping("/{key}/draft")
    @RawResponse
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("system.parameter.update")
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.update','SCOPE_platform.parameter.update','SCOPE_system.parameter.write','SCOPE_platform.parameter.write')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> draft(@PathVariable String key,
                                                            @RequestBody SystemParameterDraftRequest request,
                                                            Authentication authentication) {
        try {
            return body(HttpStatus.CREATED, release(releases.draft(key, request.toCommand(), rid(), authentication)));
        } catch (SystemParameterReleaseValidationException exception) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_PARAMETER_INVALID", exception.getMessage(), violations(exception.violations()));
        } catch (SystemParameterVersionConflictException exception) {
            return failure(HttpStatus.CONFLICT, "SUMMER_PARAMETER_VERSION_CONFLICT", exception.getMessage(), List.of());
        } catch (SystemParameterNotFoundException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", exception.getMessage(), List.of());
        } catch (SystemParameterReleaseConflictException exception) {
            return failure(HttpStatus.CONFLICT, "SUMMER_PARAMETER_RELEASE_CONFLICT", exception.getMessage(), List.of());
        }
    }

    @GetMapping("/{key}/versions")
    @RawResponse
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("system.parameter.read")
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.read','SCOPE_platform.parameter.read','SCOPE_customer.read')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> versions(@PathVariable String key, Authentication authentication) {
        try {
            return body(HttpStatus.OK, versionPage(releases.versions(key, authentication)));
        } catch (SystemParameterNotFoundException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", exception.getMessage(), List.of());
        }
    }

    @PostMapping("/{key}/publish")
    @RawResponse
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("system.parameter.publish")
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.publish','SCOPE_platform.parameter.publish','SCOPE_system.parameter.write','SCOPE_platform.parameter.write')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publish(@PathVariable String key,
                                                               @RequestBody SystemParameterReleaseRequest command,
                                                               Authentication authentication) {
        try {
            return body(HttpStatus.OK, release(releases.publish(key, command.toCommand(), rid(), authentication)));
        } catch (SystemParameterReleaseValidationException exception) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_PARAMETER_INVALID", exception.getMessage(), violations(exception.violations()));
        } catch (SystemParameterVersionConflictException | SystemParameterReleaseConflictException exception) {
            return failure(HttpStatus.CONFLICT, "SUMMER_PARAMETER_VERSION_CONFLICT", exception.getMessage(), List.of());
        } catch (SystemParameterReleaseNotFoundException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", exception.getMessage(), List.of());
        } catch (SystemParameterNotFoundException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", exception.getMessage(), List.of());
        }
    }

    @PostMapping("/{key}/rollback")
    @RawResponse
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("system.parameter.rollback")
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.rollback','SCOPE_platform.parameter.rollback','SCOPE_system.parameter.write','SCOPE_platform.parameter.write')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rollback(@PathVariable String key,
                                                                @RequestBody SystemParameterReleaseRequest command,
                                                                Authentication authentication) {
        try {
            return body(HttpStatus.OK, release(releases.rollback(key, command.toCommand(), rid(), authentication)));
        } catch (SystemParameterReleaseValidationException exception) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_PARAMETER_INVALID", exception.getMessage(), violations(exception.violations()));
        } catch (SystemParameterVersionConflictException | SystemParameterReleaseConflictException exception) {
            return failure(HttpStatus.CONFLICT, "SUMMER_PARAMETER_VERSION_CONFLICT", exception.getMessage(), List.of());
        } catch (SystemParameterReleaseNotFoundException | SystemParameterNotFoundException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", exception.getMessage(), List.of());
        }
    }

    @PostMapping("/{key}/deactivate")
    @RawResponse
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("system.parameter.disable")
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.disable','SCOPE_platform.parameter.disable','SCOPE_system.parameter.write','SCOPE_platform.parameter.write')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deactivate(@PathVariable String key,
                                                                  @RequestBody SystemParameterReleaseRequest command,
                                                                  Authentication authentication) {
        try {
            return body(HttpStatus.OK, release(releases.deactivate(key, command.toCommand(), rid(), authentication)));
        } catch (SystemParameterReleaseValidationException exception) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_PARAMETER_INVALID", exception.getMessage(), violations(exception.violations()));
        } catch (SystemParameterVersionConflictException | SystemParameterReleaseConflictException exception) {
            return failure(HttpStatus.CONFLICT, "SUMMER_PARAMETER_VERSION_CONFLICT", exception.getMessage(), List.of());
        } catch (SystemParameterNotFoundException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", exception.getMessage(), List.of());
        }
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> status(
            String key, SystemParameterStatus status, Authentication authentication) {
        try {
            return body(HttpStatus.OK, parameter(service.setStatus(key, status, rid(), authentication)));
        } catch (SystemParameterNotFoundException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", exception.getMessage(), List.of());
        } catch (SystemParameterVersionConflictException exception) {
            return failure(HttpStatus.CONFLICT, "SUMMER_PARAMETER_VERSION_CONFLICT",
                    exception.getMessage(), List.of());
        }
    }

    @PutMapping("/{key}/overrides/{organization}")
    @RawResponse
    @com.abluepoint.summer.sample.resource.api.ResourcePermission("system.parameter.publish")
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.publish','SCOPE_system.parameter.write','SCOPE_platform.parameter.write')")
    public ResponseEntity<ApiResponse<com.abluepoint.summer.sample.configuration.application.view.ConfigurationOverrideView>> override(
            @PathVariable String key, @PathVariable String organization,
            @RequestBody com.abluepoint.summer.sample.configuration.api.request.ConfigurationOverrideRequest request, Authentication auth) {
        try { return body(HttpStatus.OK, service.override(key, organization, request.toCommand(), auth, rid())); }
        catch (SystemParameterValidationException e) { return failure(HttpStatus.BAD_REQUEST, "SUMMER_PARAMETER_INVALID", e.getMessage(), violations(e.violations())); }
        catch (SystemParameterNotFoundException e) { return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", "The requested resource was not found", List.of()); }
    }

    private String rid() {
        return requestId.currentRequestId();
    }

    private <T> ResponseEntity<ApiResponse<T>> body(HttpStatus status, T value) {
        return ResponseEntity.status(status).header("X-Request-Id", rid())
                .body(ApiResponse.success(value, rid()));
    }

    private <T> ResponseEntity<ApiResponse<T>> failure(
            HttpStatus status, String code, String message, List<ApiError.FieldViolation> violations) {
        return ResponseEntity.status(status).header("X-Request-Id", rid())
                .body(ApiResponse.failure(new ApiError(code, message, violations), rid()));
    }

    private static List<ApiError.FieldViolation> violations(List<SummerException.Violation> violations) {
        return violations.stream().map(value -> new ApiError.FieldViolation(value.field(), value.message())).toList();
    }

    private static Map<String, Object> page(SystemParameterQueryPageView page) {
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

    private static Map<String, Object> release(SystemParameterReleaseView value) {
        Map<String, Object> result = parameter(value.parameter());
        result.put("revision", value.revision());
        result.put("state", value.state());
        return result;
    }

    private static Map<String, Object> versionPage(SystemParameterVersionPageView value) {
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
