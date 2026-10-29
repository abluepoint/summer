package com.abluepoint.summer.sample.systemparameter.application.command;
import java.math.BigDecimal;
import com.abluepoint.summer.sample.systemparameter.domain.*;
public record SystemParameterDraftCommand(Object value, Boolean required, BigDecimal min, BigDecimal max, String pattern, Boolean publicValue, String description, Long baseVersion, String idempotencyKey) {}
