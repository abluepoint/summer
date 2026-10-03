package com.abluepoint.summer.sample.bff;

import java.time.Instant;
import java.util.Set;

record OidcIdentity(String subject, String displayName, Instant expiresAt, Set<String> enterpriseIds) {
    OidcIdentity(String subject, String displayName, Instant expiresAt) {
        this(subject, displayName, expiresAt, Set.of());
    }

    OidcIdentity {
        enterpriseIds = enterpriseIds == null ? Set.of() : Set.copyOf(enterpriseIds);
    }
}
