package com.abluepoint.summer.sample.bff.domain;

public interface ProductSessionStore {
    void put(String id, OidcIdentity identity);
    void remove(String id);
    OidcIdentity find(String id);
    int size();
}
