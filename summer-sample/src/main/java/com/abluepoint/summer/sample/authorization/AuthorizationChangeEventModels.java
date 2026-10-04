package com.abluepoint.summer.sample.authorization;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record AuthorizationChangeEventView(String eventId, String enterpriseId, String resourceType, String resourceId,
                                    String eventType, long version, String requestId, AuthorizationChangeEventStatus status,
                                    int attempts, Instant nextAttemptAt, String lastError, Instant createdAt, Instant updatedAt,
                                    Map<String, Object> payload) {}
record AuthorizationChangeEventPage(List<AuthorizationChangeEventView> items, int totalElements) {}
record AuthorizationChangeEventBody(String eventId, String enterpriseId, String resourceType, String resourceId,
                                    String eventType, long version, String requestId) {}
