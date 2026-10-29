package com.abluepoint.summer.sample.systemparameter.infrastructure;
import com.abluepoint.summer.sample.systemparameter.api.request.SystemParameterProjection;
import com.abluepoint.summer.sample.systemparameter.api.request.SystemParameterSortDirection;
import com.abluepoint.summer.sample.systemparameter.api.request.SystemParameterSortField;

import com.abluepoint.summer.sample.systemparameter.api.request.SystemParameterListRequest;

import com.abluepoint.summer.data.jpa.query.api.JpaSqlTemplate;
import com.abluepoint.summer.sample.SampleDataProperties;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SystemParameterQueryAdapter {

    private final JpaSqlTemplate sqlTemplate;
    private final SampleDataProperties dataProperties;

    public SystemParameterQueryAdapter(JpaSqlTemplate sqlTemplate, SampleDataProperties dataProperties) {
        this.sqlTemplate = sqlTemplate;
        this.dataProperties = dataProperties;
    }

    public List<?> list(SystemParameterListRequest query) {
        Class<?> projection = query.projection() == SystemParameterProjection.SUMMARY
                ? SystemParameterSummaryRow.class : SystemParameterDetailRow.class;
        return sqlTemplate.query(dataProperties.sqlResource(), "listSystemParameters",
                templateParameters(query), bindingParameters(query), projection);
    }

    public long count(SystemParameterListRequest query) {
        return sqlTemplate.count(dataProperties.sqlResource(), "countSystemParameters",
                templateParameters(query), countBindingParameters(query));
    }

    private Map<String, Object> templateParameters(SystemParameterListRequest query) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("filterByKey", query.key() != null);
        parameters.put("filterByType", query.type() != null);
        parameters.put("filterByStatus", query.status() != null);
        parameters.put("filterByPublic", query.publicValue() != null);
        parameters.put("sortByKey", query.sort() == SystemParameterSortField.KEY);
        parameters.put("sortByType", query.sort() == SystemParameterSortField.TYPE);
        parameters.put("sortByStatus", query.sort() == SystemParameterSortField.STATUS);
        parameters.put("sortByCreatedAt", query.sort() == SystemParameterSortField.CREATED_AT);
        parameters.put("sortByUpdatedAt", query.sort() == SystemParameterSortField.UPDATED_AT);
        parameters.put("sortByVersion", query.sort() == SystemParameterSortField.VERSION);
        parameters.put("descending", query.direction() == SystemParameterSortDirection.DESC);
        parameters.put("summaryProjection", query.projection() == SystemParameterProjection.SUMMARY);
        if (query.scope() == null) throw new IllegalArgumentException("A validated data scope is required");
        // This fragment is generated only from the closed DataScopeExpression grammar; values are bound below.
        parameters.put("dataScopePredicate", query.scope().sqlPredicate());
        return parameters;
    }

    private Map<String, Object> bindingParameters(SystemParameterListRequest query) {
        Map<String, Object> bindings = countBindingParameters(query);
        bindings = new LinkedHashMap<>(bindings);
        bindings.put("limit", query.size());
        bindings.put("offset", query.offset());
        return bindings;
    }

    private Map<String, Object> countBindingParameters(SystemParameterListRequest query) {
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
