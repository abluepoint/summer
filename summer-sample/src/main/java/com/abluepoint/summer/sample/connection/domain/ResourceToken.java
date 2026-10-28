package com.abluepoint.summer.sample.connection.domain;


import java.time.Instant;
import java.util.Optional;

@org.springframework.modulith.NamedInterface("execution")
public record ResourceToken(String enterpriseId, String tokenRef, String audience, Instant expiresAt, String rawToken) {
    public Optional<String> tokenValue() {
        return Optional.ofNullable(rawToken);
    }
}
