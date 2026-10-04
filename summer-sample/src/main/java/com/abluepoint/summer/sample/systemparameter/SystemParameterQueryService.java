package com.abluepoint.summer.sample.systemparameter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import com.abluepoint.summer.sample.authorization.DataScopeService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
class SystemParameterQueryService {
    private final SystemParameterQuery query;
    private final ObjectMapper objectMapper;
    private final DataScopeService dataScopes;

    SystemParameterQueryService(SystemParameterQuery query, ObjectMapper objectMapper, DataScopeService dataScopes) {
        this.query = query;
        this.objectMapper = objectMapper;
        this.dataScopes = dataScopes;
    }

    @Transactional(readOnly = true)
    SystemParameterQueryPage list(SystemParameterListQuery request, Authentication authentication) {
        DataScopeService.ScopeFilter scope = dataScopes.filter(request.tenantId(), "system-parameters", authentication);
        request = request.withScope(scope);
        final SystemParameterListQuery scopedRequest = request;
        long total = query.count(scopedRequest);
        List<?> items = query.list(scopedRequest).stream()
                .map(value -> scopedRequest.projection() == SystemParameterListQuery.Projection.SUMMARY
                        ? summary((SystemParameterSummaryRow) value)
                        : detail((SystemParameterDetailRow) value))
                .toList();
        int totalPages = total == 0 ? 0 : (int) ((total - 1) / request.size()) + 1;
        return new SystemParameterQueryPage(items, scopedRequest.page(), scopedRequest.size(), total, totalPages,
                scopedRequest.projection().name().toLowerCase(java.util.Locale.ROOT));
    }

    SystemParameterQueryPage list(SystemParameterListQuery request) { return list(request, null); }

    private SystemParameterView detail(SystemParameterDetailRow row) {
        return new SystemParameterView(row.id(), row.parameterKey(), SystemParameterType.valueOf(row.valueType()),
                deserialize(row.valueJson()), Boolean.TRUE.equals(row.requiredFlag()), row.minValue(), row.maxValue(),
                row.valuePattern(), Boolean.TRUE.equals(row.publicFlag()), row.description(),
                SystemParameterStatus.valueOf(row.status()), row.version(), row.tenantId(), row.createdAt(), row.updatedAt());
    }

    private SystemParameterSummaryView summary(SystemParameterSummaryRow row) {
        return new SystemParameterSummaryView(row.id(), row.parameterKey(),
                SystemParameterType.valueOf(row.valueType()), SystemParameterStatus.valueOf(row.status()),
                Boolean.TRUE.equals(row.publicFlag()));
    }

    private Object deserialize(String value) {
        if (value == null) return null;
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("System parameter value cannot be read", exception);
        }
    }
}
