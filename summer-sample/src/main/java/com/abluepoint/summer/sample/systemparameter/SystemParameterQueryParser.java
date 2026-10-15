package com.abluepoint.summer.sample.systemparameter;

import static com.abluepoint.summer.sample.configuration.ConfigurationTenantContext.resolve;

import com.abluepoint.summer.mvc.validate.FieldValues;
import org.springframework.security.core.Authentication;

final class SystemParameterQueryParser {
    private SystemParameterQueryParser() {}

    static SystemParameterListQuery parse(FieldValues values, Authentication authentication) {
        int page = values.get("page", Integer.class) == null ? 0 : values.get("page", Integer.class);
        int size = values.get("size", Integer.class) == null ? 20 : values.get("size", Integer.class);
        String tenantId = resolve(authentication);
        String type = values.get("type", String.class);
        String status = values.get("status", String.class);
        String sort = values.get("sort", String.class);
        String direction = values.get("direction", String.class);
        String projection = values.get("projection", String.class);
        return new SystemParameterListQuery(
                page, size, values.get("key", String.class),
                type == null ? null : SystemParameterType.valueOf(type),
                status == null ? null : SystemParameterStatus.valueOf(status),
                values.get("public", Boolean.class),
                sort == null ? SystemParameterListQuery.SortField.KEY : sortField(sort),
                "desc".equals(direction) ? SystemParameterListQuery.SortDirection.DESC : SystemParameterListQuery.SortDirection.ASC,
                "summary".equals(projection) ? SystemParameterListQuery.Projection.SUMMARY : SystemParameterListQuery.Projection.DETAIL,
                tenantId, null);
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

}
