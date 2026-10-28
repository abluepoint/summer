package com.abluepoint.summer.sample.identity.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SessionRecord(
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
    public SessionRecord {
        claims = claims == null ? Map.of() : Map.copyOf(claims);
        authorities = authorities == null ? List.of() : List.copyOf(authorities);
    }
    public boolean expiredAt(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public boolean activeAt(Instant now) {
        return "ACTIVE".equals(status) && expiresAt != null && expiresAt.isAfter(now);
    }

    public SessionRecord revokeAt(Instant now) {
        return new SessionRecord(sessionRef, principal, clientId, "REVOKED", createdAt, now, expiresAt, claims, authorities);
    }

    public SessionRecord touchAt(Instant now, int idleInterval) {
        if (!"ACTIVE".equals(status) || expiredAt(now)) return null;
        return new SessionRecord(sessionRef, principal, clientId, status, createdAt, now,
                now.plusSeconds(idleInterval > 0 ? idleInterval : 1_800), claims, authorities);
    }
}
