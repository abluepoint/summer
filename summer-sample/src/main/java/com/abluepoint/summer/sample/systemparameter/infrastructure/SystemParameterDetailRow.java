package com.abluepoint.summer.sample.systemparameter.infrastructure;

import java.math.BigDecimal;

public record SystemParameterDetailRow(
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
        Object createdAt,
        Object updatedAt
) {
}
