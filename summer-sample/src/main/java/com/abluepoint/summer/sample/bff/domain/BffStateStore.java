package com.abluepoint.summer.sample.bff.domain;

public interface BffStateStore {
    void save(String state, String codeVerifier);
    String consume(String state, String cookieState);
}
