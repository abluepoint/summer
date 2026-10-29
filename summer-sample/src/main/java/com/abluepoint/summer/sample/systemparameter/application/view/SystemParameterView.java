package com.abluepoint.summer.sample.systemparameter.application.view;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterStatus;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterType;

import java.math.BigDecimal;
import java.time.Instant;

@org.springframework.modulith.NamedInterface("views")
public record SystemParameterView(
        String id,
        String key,
        SystemParameterType type,
        Object value,
        boolean required,
        BigDecimal min,
        BigDecimal max,
        String pattern,
        boolean publicValue,
        String description,
        SystemParameterStatus status,
        long version,
        String tenantId,
        Instant createdAt,
        Instant updatedAt
) {
}
