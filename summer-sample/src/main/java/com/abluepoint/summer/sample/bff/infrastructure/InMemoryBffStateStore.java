package com.abluepoint.summer.sample.bff.infrastructure;

import com.abluepoint.summer.sample.bff.domain.BffStateStore;
import com.abluepoint.summer.sample.bff.domain.PendingAuthorization;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryBffStateStore implements BffStateStore {

    private final Map<String, PendingAuthorization> states = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryBffStateStore() {
        this(Clock.systemUTC());
    }

    public InMemoryBffStateStore(Clock clock) {
        this.clock = clock;
    }

    public void save(String state, String codeVerifier) {
        states.put(state, new PendingAuthorization(
                codeVerifier,
                Instant.now(clock).plus(Duration.ofMinutes(5))
        ));
    }

    public String consume(String state, String cookieState) {
        if (!PendingAuthorization.matches(state, cookieState)) {
            return null;
        }
        PendingAuthorization authorization = states.remove(state);
        if (authorization == null || authorization.expiredAt(Instant.now(clock))) {
            return null;
        }
        return authorization.codeVerifier();
    }

}
