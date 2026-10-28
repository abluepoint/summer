package com.abluepoint.summer.sample.authorization.application;

public record AuthorizationChangeEvent(String eventId, String enterpriseId, String resourceType, String resourceId,
                                    String eventType, long version, String requestId) {}
