package com.abluepoint.summer.sample.authorization.application.view;

import com.abluepoint.summer.sample.authorization.domain.AuthorizationChangeEventStatus;

import java.time.Instant;
import java.util.Map;

public record AuthorizationChangeEventView(String eventId, String enterpriseId, String resourceType, String resourceId,
                                    String eventType, long version, String requestId, AuthorizationChangeEventStatus status,
                                    int attempts, Instant nextAttemptAt, String lastError, Instant createdAt, Instant updatedAt,
                                    Map<String, Object> payload) {}
