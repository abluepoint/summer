package com.abluepoint.summer.sample.systemparameter;

import com.abluepoint.summer.data.jpa.query.api.JpaSqlTemplate;
import com.abluepoint.summer.sample.SampleDataProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class SystemParameterQuery {

    private final JpaSqlTemplate sqlTemplate;
    private final SampleDataProperties dataProperties;

    SystemParameterQuery(JpaSqlTemplate sqlTemplate, SampleDataProperties dataProperties) {
        this.sqlTemplate = sqlTemplate;
        this.dataProperties = dataProperties;
    }

    List<?> list(SystemParameterListQuery query) {
        Class<?> projection = query.projection() == SystemParameterListQuery.Projection.SUMMARY
                ? SystemParameterSummaryRow.class : SystemParameterDetailRow.class;
        return sqlTemplate.query(dataProperties.sqlResource(), "listSystemParameters",
                templateParameters(query), bindingParameters(query), projection);
    }

    long count(SystemParameterListQuery query) {
        return sqlTemplate.count(dataProperties.sqlResource(), "countSystemParameters",
                templateParameters(query), countBindingParameters(query));
    }

    private Map<String, Object> templateParameters(SystemParameterListQuery query) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("filterByKey", query.key() != null);
        parameters.put("filterByType", query.type() != null);
        parameters.put("filterByStatus", query.status() != null);
        parameters.put("filterByPublic", query.publicValue() != null);
        parameters.put("sortByKey", query.sort() == SystemParameterListQuery.SortField.KEY);
        parameters.put("sortByType", query.sort() == SystemParameterListQuery.SortField.TYPE);
        parameters.put("sortByStatus", query.sort() == SystemParameterListQuery.SortField.STATUS);
        parameters.put("sortByCreatedAt", query.sort() == SystemParameterListQuery.SortField.CREATED_AT);
        parameters.put("sortByUpdatedAt", query.sort() == SystemParameterListQuery.SortField.UPDATED_AT);
        parameters.put("sortByVersion", query.sort() == SystemParameterListQuery.SortField.VERSION);
        parameters.put("descending", query.direction() == SystemParameterListQuery.SortDirection.DESC);
        parameters.put("summaryProjection", query.projection() == SystemParameterListQuery.Projection.SUMMARY);
        if (query.scope() == null) throw new IllegalArgumentException("A validated data scope is required");
        // This fragment is generated only from the closed DataScopeExpression grammar; values are bound below.
        parameters.put("dataScopePredicate", query.scope().sqlPredicate());
        return parameters;
    }

    private Map<String, Object> bindingParameters(SystemParameterListQuery query) {
        Map<String, Object> bindings = countBindingParameters(query);
        bindings = new LinkedHashMap<>(bindings);
        bindings.put("limit", query.size());
        bindings.put("offset", query.offset());
        return bindings;
    }

    private Map<String, Object> countBindingParameters(SystemParameterListQuery query) {
        Map<String, Object> bindings = new LinkedHashMap<>();
        bindings.put("tenantId", query.tenantId());
        if (query.scope() == null) throw new IllegalArgumentException("A validated data scope is required");
        bindings.putAll(query.scope().sqlBindings());
        if (query.key() != null) bindings.put("keyPattern", "%" + query.key() + "%");
        if (query.type() != null) bindings.put("type", query.type().name());
        if (query.status() != null) bindings.put("status", query.status().name());
        if (query.publicValue() != null) bindings.put("publicValue", query.publicValue());
        return bindings;
    }
}

record SystemParameterListQuery(
        int page,
        int size,
        String key,
        SystemParameterType type,
        SystemParameterStatus status,
        Boolean publicValue,
        SortField sort,
        SortDirection direction,
        Projection projection,
        String tenantId,
        com.abluepoint.summer.sample.authorization.DataScopeService.ScopeFilter scope
) {
    static final int DEFAULT_PAGE = 0;
    static final int DEFAULT_SIZE = 20;

    SystemParameterListQuery {
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        if (key != null && key.isBlank()) throw new IllegalArgumentException("key must not be blank");
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
    }

    SystemParameterListQuery withScope(com.abluepoint.summer.sample.authorization.DataScopeService.ScopeFilter scope) {
        return new SystemParameterListQuery(page, size, key, type, status, publicValue, sort, direction, projection, tenantId, scope);
    }

    long offset() {
        return Math.multiplyExact((long) page, size);
    }

    enum SortField { KEY, TYPE, STATUS, CREATED_AT, UPDATED_AT, VERSION }
    enum SortDirection { ASC, DESC }
    enum Projection { SUMMARY, DETAIL }
}
