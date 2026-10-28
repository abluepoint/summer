package com.abluepoint.summer.sample.identity.domain;

import java.time.Duration;
import java.util.Set;

public interface SessionIndexStore {
    /** One bounded storage operation; retries must cover the complete session mutation. */
    <T> T execute(java.util.concurrent.Callable<T> operation);
    SessionRecord read(String reference);
    Set<String> references(String principal);
    void removeReference(String principal, String reference);
    boolean writeActive(SessionRecord record, Duration ttl, boolean create);
    void writeRevoked(SessionRecord record);
}
