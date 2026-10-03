package com.abluepoint.summer.sample.connection;

import java.time.Instant;
import java.util.Optional;

public record ResourceToken(String enterpriseId, String tokenRef, String audience, Instant expiresAt, String rawToken) {
    public Optional<String> tokenValue() {
        return Optional.ofNullable(rawToken);
    }
}
