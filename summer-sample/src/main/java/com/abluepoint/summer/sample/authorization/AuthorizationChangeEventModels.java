package com.abluepoint.summer.sample.authorization;

record AuthorizationChangeEventBody(String eventId, String enterpriseId, String resourceType, String resourceId,
                                    String eventType, long version, String requestId, String domainKey) {
    AuthorizationChangeEventBody(String eventId, String enterpriseId, String resourceType, String resourceId,
                                 String eventType, long version, String requestId) {
        this(eventId, enterpriseId, resourceType, resourceId, eventType, version, requestId, "");
    }
}
