package com.abluepoint.summer.sample.systemparameter.domain;

import java.math.BigDecimal;

public record SystemParameterSnapshot(
        SystemParameterType type, Object value, boolean required, BigDecimal min, BigDecimal max,
        String pattern, boolean publicValue, String description, SystemParameterStatus status) {}
