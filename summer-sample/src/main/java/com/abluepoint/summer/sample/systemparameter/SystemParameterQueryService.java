package com.abluepoint.summer.sample.systemparameter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
class SystemParameterQueryService {
    private final SystemParameterQuery query;
    private final ObjectMapper objectMapper;

    SystemParameterQueryService(SystemParameterQuery query, ObjectMapper objectMapper) {
        this.query = query;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    SystemParameterQueryPage list(SystemParameterListQuery request) {
        long total = query.count(request);
        List<?> items = query.list(request).stream()
                .map(value -> request.projection() == SystemParameterListQuery.Projection.SUMMARY
                        ? summary((SystemParameterSummaryRow) value)
                        : detail((SystemParameterDetailRow) value))
                .toList();
        int totalPages = total == 0 ? 0 : (int) ((total - 1) / request.size()) + 1;
        return new SystemParameterQueryPage(items, request.page(), request.size(), total, totalPages,
                request.projection().name().toLowerCase(java.util.Locale.ROOT));
    }

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
