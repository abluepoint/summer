package com.abluepoint.summer.sample.systemparameter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import com.abluepoint.summer.sample.authorization.application.DataScopeService;
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
        final SystemParameterListQuery scopedRequest = scoped(request, authentication);
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

    @Transactional(readOnly = true)
    SystemParameterQueryCount count(SystemParameterListQuery request, Authentication authentication) {
        return new SystemParameterQueryCount(query.count(scoped(request, authentication)));
    }

    private SystemParameterListQuery scoped(SystemParameterListQuery request, Authentication authentication) {
        return request.withScope(dataScopes.filter(request.tenantId(), "system-parameters", authentication));
    }

    private SystemParameterView detail(SystemParameterDetailRow row) {
        return new SystemParameterView(row.id(), row.parameterKey(), SystemParameterType.valueOf(row.valueType()),
                deserialize(row.valueJson()), Boolean.TRUE.equals(row.requiredFlag()), row.minValue(), row.maxValue(),
                row.valuePattern(), Boolean.TRUE.equals(row.publicFlag()), row.description(),
                SystemParameterStatus.valueOf(row.status()), row.version(), row.tenantId(), instant(row.createdAt()), instant(row.updatedAt()));
    }

    private SystemParameterSummaryView summary(SystemParameterSummaryRow row) {
        return new SystemParameterSummaryView(row.id(), row.parameterKey(),
                SystemParameterType.valueOf(row.valueType()), SystemParameterStatus.valueOf(row.status()),
                Boolean.TRUE.equals(row.publicFlag()));
    }

    private static java.time.Instant instant(Object value) {
        if (value instanceof java.time.Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        // MySQL DATETIME has no zone; this application binds JDBC/Hibernate timestamps in UTC.
        if (value instanceof java.time.LocalDateTime local) return local.toInstant(java.time.ZoneOffset.UTC);
        if (value instanceof java.time.OffsetDateTime offset) return offset.toInstant();
        throw new IllegalStateException("Unsupported configuration timestamp projection");
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
