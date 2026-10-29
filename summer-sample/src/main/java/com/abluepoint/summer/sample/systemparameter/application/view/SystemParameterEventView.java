package com.abluepoint.summer.sample.systemparameter.application.view;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterEventBody;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterEventStatus;

import java.time.Instant;

public record SystemParameterEventView(
        String eventId, String tenantId, String parameterKey, String eventType, long version,
        String requestId, SystemParameterEventStatus status, int attempts, Instant nextAttemptAt,
        String lastError, Instant createdAt, Instant updatedAt, SystemParameterEventBody event) {}
