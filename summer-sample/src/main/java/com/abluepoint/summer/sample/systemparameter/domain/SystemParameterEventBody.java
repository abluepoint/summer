package com.abluepoint.summer.sample.systemparameter.domain;

public record SystemParameterEventBody(
        String eventId, String tenantId, String parameterKey, String eventType, long version,
        String requestId) {}
