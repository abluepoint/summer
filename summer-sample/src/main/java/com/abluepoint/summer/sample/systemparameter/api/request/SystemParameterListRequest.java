package com.abluepoint.summer.sample.systemparameter.api.request;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterStatus;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterType;

import com.abluepoint.summer.sample.authorization.application.DataScopeService;

public record SystemParameterListRequest(
        int page,
        int size,
        String key,
        SystemParameterType type,
        SystemParameterStatus status,
        Boolean publicValue,
        SystemParameterSortField sort,
        SystemParameterSortDirection direction,
        SystemParameterProjection projection,
        String tenantId,
        com.abluepoint.summer.sample.authorization.application.DataScopeService.ScopeFilter scope
) {
    static final int DEFAULT_PAGE = 0;
    static final int DEFAULT_SIZE = 20;

    public SystemParameterListRequest {
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        if (key != null && key.isBlank()) throw new IllegalArgumentException("key must not be blank");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
    }

    public SystemParameterListRequest withScope(com.abluepoint.summer.sample.authorization.application.DataScopeService.ScopeFilter scope) {
        return new SystemParameterListRequest(page, size, key, type, status, publicValue, sort, direction, projection, tenantId, scope);
    }

    public long offset() {
        return Math.multiplyExact((long) page, size);
    }

}
