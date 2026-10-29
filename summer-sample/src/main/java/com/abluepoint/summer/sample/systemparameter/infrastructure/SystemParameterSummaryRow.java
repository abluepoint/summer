package com.abluepoint.summer.sample.systemparameter.infrastructure;

public record SystemParameterSummaryRow(
        String id,
        String parameterKey,
        String valueType,
        String status,
        Boolean publicFlag
) {
}
