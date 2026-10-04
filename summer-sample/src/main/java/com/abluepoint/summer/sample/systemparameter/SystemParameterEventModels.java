package com.abluepoint.summer.sample.systemparameter;

import java.time.Instant;
import java.util.List;

record SystemParameterEventView(
        String eventId, String tenantId, String parameterKey, String eventType, long version,
        String requestId, SystemParameterEventStatus status, int attempts, Instant nextAttemptAt,
        String lastError, Instant createdAt, Instant updatedAt, SystemParameterEventBody event) {}

record SystemParameterEventBody(
        String eventId, String tenantId, String parameterKey, String eventType, long version,
        String requestId) {}

record SystemParameterEventPage(List<SystemParameterEventView> items, int totalElements) {}
