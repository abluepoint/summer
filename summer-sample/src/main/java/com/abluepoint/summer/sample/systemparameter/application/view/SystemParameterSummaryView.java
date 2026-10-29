package com.abluepoint.summer.sample.systemparameter.application.view;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterStatus;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterType;

public record SystemParameterSummaryView(
        String id,
        String key,
        SystemParameterType type,
        SystemParameterStatus status,
        boolean publicValue
) {
}
