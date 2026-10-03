package com.abluepoint.summer.sample.bff;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class InMemoryProductSessionStore {

    private final Map<String, OidcIdentity> sessions = new ConcurrentHashMap<>();

    void put(String id, OidcIdentity identity) {
        sessions.put(id, identity);
    }

    void remove(String id) {
        if (id != null) {
            sessions.remove(id);
        }
    }

    OidcIdentity find(String id) {
        return id == null ? null : sessions.get(id);
    }

    int size() {
        return sessions.size();
    }
}
