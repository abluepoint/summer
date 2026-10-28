package com.abluepoint.summer.sample.bff.infrastructure;

import com.abluepoint.summer.sample.bff.domain.ProductSessionStore;

import com.abluepoint.summer.sample.bff.domain.OidcIdentity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryProductSessionStore implements ProductSessionStore {

    private final Map<String, OidcIdentity> sessions = new ConcurrentHashMap<>();

    public void put(String id, OidcIdentity identity) {
        sessions.put(id, identity);
    }

    public void remove(String id) {
        if (id != null) {
            sessions.remove(id);
        }
    }

    public OidcIdentity find(String id) {
        return id == null ? null : sessions.get(id);
    }

    public int size() {
        return sessions.size();
    }
}
