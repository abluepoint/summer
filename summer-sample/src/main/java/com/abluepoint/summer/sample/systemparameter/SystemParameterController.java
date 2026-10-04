package com.abluepoint.summer.sample.systemparameter;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.response.ApiError;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system-parameters")
class SystemParameterController {
    private final SystemParameterService service;
    private final SystemParameterQueryService queryService;
    private final RequestIdAccessor requestId;

    SystemParameterController(SystemParameterService service, SystemParameterQueryService queryService, RequestIdAccessor requestId) {
        this.service = service;
        this.queryService = queryService;
        this.requestId = requestId;
    }

    @GetMapping
    @RawResponse
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.read','SCOPE_platform.parameter.read','SCOPE_customer.read')")
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
            FieldValues values) {
        try {
            int page = values.get("page", Integer.class) == null ? 0 : values.get("page", Integer.class);
            int size = values.get("size", Integer.class) == null ? 20 : values.get("size", Integer.class);
            String tenantId = values.get("tenantId", String.class);
            if (tenantId != null && !SystemParameterService.TENANT_ID.equals(tenantId)) {
                return failure(HttpStatus.FORBIDDEN, "SUMMER_FORBIDDEN", "System parameter tenant access denied", List.of());
            }
            String type = values.get("type", String.class);
            String status = values.get("status", String.class);
            String sort = values.get("sort", String.class);
            String direction = values.get("direction", String.class);
            String projection = values.get("projection", String.class);
            SystemParameterListQuery query = new SystemParameterListQuery(
                    page, size, values.get("key", String.class),
                    type == null ? null : SystemParameterType.valueOf(type),
                    status == null ? null : SystemParameterStatus.valueOf(status),
                    values.get("public", Boolean.class),
                    sort == null ? SystemParameterListQuery.SortField.KEY : sortField(sort),
                    "desc".equals(direction) ? SystemParameterListQuery.SortDirection.DESC : SystemParameterListQuery.SortDirection.ASC,
                    "summary".equals(projection) ? SystemParameterListQuery.Projection.SUMMARY : SystemParameterListQuery.Projection.DETAIL,
                    SystemParameterService.TENANT_ID);
            return body(HttpStatus.OK, page(queryService.list(query)));
        } catch (IllegalArgumentException exception) {
            return failure(HttpStatus.BAD_REQUEST, "SUMMER_PARAMETER_INVALID", "Invalid system parameter query", List.of());
        }
    }

    @GetMapping("/{key}")
    @RawResponse
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.read','SCOPE_platform.parameter.read','SCOPE_customer.read')")
    @Field(param = "key", style = "systemParameterKeyStyle")
    ResponseEntity<ApiResponse<Map<String, Object>>> find(
            @PathVariable String key) {
        try {
            return body(HttpStatus.OK, parameter(service.find(key)));
        } catch (SystemParameterNotFoundException exception) {
            return failure(HttpStatus.NOT_FOUND, "SUMMER_NOT_FOUND", exception.getMessage(), List.of());
        }
    }

    @PostMapping
    @RawResponse
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.write','SCOPE_platform.parameter.write','SCOPE_customer.write')")
    ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @RequestBody CreateSystemParameterRequest request,
            Authentication authentication) {
        try {
            return body(HttpStatus.CREATED, parameter(service.create(request, rid(), authentication)));
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
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.write','SCOPE_platform.parameter.write','SCOPE_customer.write')")
    ResponseEntity<ApiResponse<Map<String, Object>>> update(
            @PathVariable String key,
            @RequestBody UpdateSystemParameterRequest request,
            Authentication authentication) {
        try {
            return body(HttpStatus.OK, parameter(service.update(key, request, rid(), authentication)));
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
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.write','SCOPE_platform.parameter.write','SCOPE_customer.write')")
    @Field(param = "key", style = "systemParameterKeyStyle")
    ResponseEntity<ApiResponse<Map<String, Object>>> enable(
            @PathVariable String key,
            Authentication authentication) {
        return status(key, SystemParameterStatus.ACTIVE, authentication);
    }

    @PostMapping("/{key}/disable")
    @RawResponse
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.write','SCOPE_platform.parameter.write','SCOPE_customer.write')")
    @Field(param = "key", style = "systemParameterKeyStyle")
    ResponseEntity<ApiResponse<Map<String, Object>>> disable(
            @PathVariable String key,
            Authentication authentication) {
        return status(key, SystemParameterStatus.INACTIVE, authentication);
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

    private static SystemParameterListQuery.SortField sortField(String value) {
        return switch (value) {
            case "key" -> SystemParameterListQuery.SortField.KEY;
            case "type" -> SystemParameterListQuery.SortField.TYPE;
            case "status" -> SystemParameterListQuery.SortField.STATUS;
            case "createdAt" -> SystemParameterListQuery.SortField.CREATED_AT;
            case "updatedAt" -> SystemParameterListQuery.SortField.UPDATED_AT;
            case "version" -> SystemParameterListQuery.SortField.VERSION;
            default -> throw new IllegalArgumentException("Invalid system parameter sort field");
        };
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
}
