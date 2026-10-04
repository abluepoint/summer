package com.abluepoint.summer.sample.identity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record SessionIndexPage(String availability, List<SessionIndexView> items) {
    SessionIndexPage { items = List.copyOf(items); }
}

record SessionIndexView(
        String sessionRef,
        String principal,
        String clientId,
        String status,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        boolean current
) {}

record SessionIndexOperation(String sessionRef, String status, String jwtStatus) {}

record SessionRecord(
        String sessionRef,
        String principal,
        String clientId,
        String status,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        Map<String, Object> claims,
        List<String> authorities
) {
    SessionRecord {
        claims = claims == null ? Map.of() : Map.copyOf(claims);
        authorities = authorities == null ? List.of() : List.copyOf(authorities);
    }
}

record SessionAuthenticationSnapshot(
        String principal,
        Map<String, Object> claims,
        List<String> authorities,
        Instant issuedAt,
        Instant expiresAt
) {}
