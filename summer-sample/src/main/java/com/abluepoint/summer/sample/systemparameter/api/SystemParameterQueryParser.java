package com.abluepoint.summer.sample.systemparameter.api;
import com.abluepoint.summer.sample.systemparameter.api.request.SystemParameterProjection;
import com.abluepoint.summer.sample.systemparameter.api.request.SystemParameterSortDirection;
import com.abluepoint.summer.sample.systemparameter.api.request.SystemParameterSortField;

import com.abluepoint.summer.sample.systemparameter.api.request.SystemParameterListRequest;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterStatus;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterType;

import static com.abluepoint.summer.sample.configuration.infrastructure.ConfigurationTenantContext.resolve;

import com.abluepoint.summer.mvc.parameter.api.FieldValues;
import org.springframework.security.core.Authentication;

public final class SystemParameterQueryParser {
    private SystemParameterQueryParser() {}

    public static SystemParameterListRequest parse(FieldValues values, Authentication authentication) {
        int page = values.get("page", Integer.class) == null ? 0 : values.get("page", Integer.class);
        int size = values.get("size", Integer.class) == null ? 20 : values.get("size", Integer.class);
        String tenantId = resolve(authentication);
        String type = values.get("type", String.class);
        String status = values.get("status", String.class);
        String sort = values.get("sort", String.class);
        String direction = values.get("direction", String.class);
        String projection = values.get("projection", String.class);
        return new SystemParameterListRequest(
                page, size, values.get("key", String.class),
                type == null ? null : SystemParameterType.valueOf(type),
                status == null ? null : SystemParameterStatus.valueOf(status),
                values.get("public", Boolean.class),
                sort == null ? SystemParameterSortField.KEY : sortField(sort),
                "desc".equals(direction) ? SystemParameterSortDirection.DESC : SystemParameterSortDirection.ASC,
                "summary".equals(projection) ? SystemParameterProjection.SUMMARY : SystemParameterProjection.DETAIL,
                tenantId, null);
    }

    private static SystemParameterSortField sortField(String value) {
        return switch (value) {
            case "key" -> SystemParameterSortField.KEY;
            case "type" -> SystemParameterSortField.TYPE;
            case "status" -> SystemParameterSortField.STATUS;
            case "createdAt" -> SystemParameterSortField.CREATED_AT;
            case "updatedAt" -> SystemParameterSortField.UPDATED_AT;
            case "version" -> SystemParameterSortField.VERSION;
            default -> throw new IllegalArgumentException("Invalid system parameter sort field");
        };
    }

}
