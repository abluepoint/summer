package com.abluepoint.summer.sample.bff;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class InMemoryBffStateStore {

    private final Map<String, PendingAuthorization> states = new ConcurrentHashMap<>();
    private final Clock clock;

    InMemoryBffStateStore() {
        this(Clock.systemUTC());
    }

    InMemoryBffStateStore(Clock clock) {
        this.clock = clock;
    }

    void save(String state, String codeVerifier) {
        states.put(state, new PendingAuthorization(
                codeVerifier,
                Instant.now(clock).plus(Duration.ofMinutes(5))
        ));
    }

    String consume(String state, String cookieState) {
        if (!matches(state, cookieState)) {
            return null;
        }
        PendingAuthorization authorization = states.remove(state);
        if (authorization == null || !authorization.expiresAt().isAfter(Instant.now(clock))) {
            return null;
        }
        return authorization.codeVerifier();
    }

    private static boolean matches(String state, String cookieState) {
        if (state == null || cookieState == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                state.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                cookieState.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    private record PendingAuthorization(String codeVerifier, Instant expiresAt) {
    }
}
