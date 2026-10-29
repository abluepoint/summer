package com.abluepoint.summer.sample.systemparameter.application.command;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterType;
import java.math.BigDecimal;
import com.abluepoint.summer.sample.systemparameter.domain.*;
public record SystemParameterCreateCommand(String key, SystemParameterType type, Object value, Boolean required, BigDecimal min, BigDecimal max, String pattern, Boolean publicValue, String description, String organizationId, String createdBy, String ownerId) {}
