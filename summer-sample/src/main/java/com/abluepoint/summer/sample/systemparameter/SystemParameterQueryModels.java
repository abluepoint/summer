package com.abluepoint.summer.sample.systemparameter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

record SystemParameterQueryPage(
        List<?> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String projection
) {
}

record SystemParameterSummaryView(
        String id,
        String key,
        SystemParameterType type,
        SystemParameterStatus status,
        boolean publicValue
) {
}

record SystemParameterDetailRow(
        String id,
        String parameterKey,
        String valueType,
        String valueJson,
        Boolean requiredFlag,
        BigDecimal minValue,
        BigDecimal maxValue,
        String valuePattern,
        Boolean publicFlag,
        String description,
        String status,
        Long version,
        String tenantId,
        Instant createdAt,
        Instant updatedAt
) {
}

record SystemParameterSummaryRow(
        String id,
        String parameterKey,
        String valueType,
        String status,
        Boolean publicFlag
) {
}
