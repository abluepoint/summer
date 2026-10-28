package com.abluepoint.summer.sample.bff.domain;

import java.time.Instant;
import java.util.Set;

public record OidcIdentity(String subject, String displayName, Instant expiresAt, Set<String> enterpriseIds) {
    public OidcIdentity(String subject, String displayName, Instant expiresAt) {
        this(subject, displayName, expiresAt, Set.of());
    }

    public OidcIdentity {
        enterpriseIds = enterpriseIds == null ? Set.of() : Set.copyOf(enterpriseIds);
    }
    public boolean activeAt(Instant now) {
        return expiresAt != null && expiresAt.isAfter(now);
    }

    public boolean permitsEnterprise(String enterpriseId) {
        return enterpriseId != null && !enterpriseId.isBlank() && enterpriseIds.contains(enterpriseId);
    }
}
