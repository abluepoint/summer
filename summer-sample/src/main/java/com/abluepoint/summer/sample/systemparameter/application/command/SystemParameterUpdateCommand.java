package com.abluepoint.summer.sample.systemparameter.application.command;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterStatus;
import java.math.BigDecimal;
import com.abluepoint.summer.sample.systemparameter.domain.*;
public record SystemParameterUpdateCommand(Object value, Boolean required, BigDecimal min, BigDecimal max, String pattern, Boolean publicValue, String description, SystemParameterStatus status, Long version) {}
