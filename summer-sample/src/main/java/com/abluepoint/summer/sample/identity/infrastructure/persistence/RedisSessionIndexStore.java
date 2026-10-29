package com.abluepoint.summer.sample.identity.infrastructure.persistence;

import com.abluepoint.summer.sample.identity.domain.SessionIndexStore;

import com.abluepoint.summer.sample.identity.domain.SessionIndexUnavailableException;
import com.abluepoint.summer.sample.identity.domain.SessionNotFoundException;
import com.abluepoint.summer.sample.identity.domain.SessionRecord;
import com.abluepoint.summer.sample.identity.domain.SessionRevokedException;
import com.abluepoint.summer.sample.identity.infrastructure.configuration.SessionIndexProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@ConditionalOnProperty(prefix = "summer.sample.session-index", name = "enabled", havingValue = "true")
public final class RedisSessionIndexStore implements SessionIndexStore {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final SessionIndexProperties properties;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "summer-session-index");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openedAt = new AtomicLong();

    public RedisSessionIndexStore(StringRedisTemplate redis, ObjectMapper mapper, SessionIndexProperties properties) {
        this.redis = redis;
        this.mapper = mapper;
        this.properties = properties;
    }
    @jakarta.annotation.PreDestroy
    void close() { executor.shutdownNow(); }

    @Override public <T> T execute(Callable<T> callback) {
        long now = System.currentTimeMillis();
        long opened = openedAt.get();
        if (opened > 0 && now - opened < properties.getOpenDurationMs()) throw new SessionIndexUnavailableException();
        if (opened > 0) openedAt.compareAndSet(opened, 0);
        for (int attempt = 0; attempt <= properties.getRetries(); attempt++) {
            Future<T> future = executor.submit(callback);
            try {
                T value = future.get(properties.getTimeoutMs(), TimeUnit.MILLISECONDS);
                consecutiveFailures.set(0);
                return value;
            } catch (TimeoutException exception) {
                future.cancel(true);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                if (cause instanceof SessionRevokedException || cause instanceof SessionNotFoundException || cause instanceof AccessDeniedException) throw (RuntimeException) cause;
            }
        }
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= properties.getFailureThreshold()) openedAt.set(System.currentTimeMillis());
        throw new SessionIndexUnavailableException();
    }

    private SessionRecord readOnce(String reference) {
        String json = redis.opsForValue().get(properties.getNamespace() + ":session:" + reference);
        if (json == null) return null;
        try { return mapper.readValue(json, SessionRecord.class); }
        catch (JacksonException exception) { throw new IllegalStateException("Invalid shared session record"); }
    }

    // Each mutation is atomic with respect to revocation. No stale ACTIVE write can
    // overwrite the tombstone, and reads cannot recreate a missing login record.
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> ACTIVE =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>("""
                local old = redis.call('GET', KEYS[1])
                if not old and ARGV[3] ~= 'create' then return 0 end
                if old then
                  local prior = cjson.decode(old)
                  local next = cjson.decode(ARGV[1])
                  if prior.status ~= 'ACTIVE' or prior.principal ~= next.principal then return 0 end
                end
                redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
                redis.call('SADD', KEYS[2], ARGV[4])
                local priorTtl = redis.call('PTTL', KEYS[2])
                if priorTtl < tonumber(ARGV[2]) then redis.call('PEXPIRE', KEYS[2], ARGV[2]) end
                return 1
                """, Long.class);
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> REVOKE =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>("""
                local old = redis.call('GET', KEYS[1])
                if not old then return 0 end
                local record = cjson.decode(old)
                record.status = 'REVOKED'
                record.lastSeenAt = ARGV[1]
                redis.call('SET', KEYS[1], cjson.encode(record), 'KEEPTTL')
                return 1
                """, Long.class);

    private boolean writeActiveOnce(SessionRecord record, Duration ttl, boolean create) {
        try {
            Long result = redis.execute(ACTIVE, List.of(properties.getNamespace() + ":session:" + record.sessionRef(),
                    principalKey(record.principal())), mapper.writeValueAsString(record), Long.toString(ttl.toMillis()),
                    create ? "create" : "touch", record.sessionRef());
            return Long.valueOf(1).equals(result);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to encode shared session record");
        }
    }

    private void writeRevokedOnce(SessionRecord record) {
        redis.execute(REVOKE, List.of(properties.getNamespace() + ":session:" + record.sessionRef()), record.lastSeenAt().toString());
    }

    private String principalKey(String principal) { return properties.getNamespace() + ":principal-sessions:" + digest(principal); }
    public static String reference(String sessionId) { return digest(sessionId); }
    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is required", exception); }
    }
    @Override public SessionRecord read(String reference) { return readOnce(reference); }
    @Override public Set<String> references(String principal) { return redis.opsForSet().members(principalKey(principal)); }
    @Override public void removeReference(String principal, String reference) {
        redis.opsForSet().remove(principalKey(principal), reference);
    }
    @Override public boolean writeActive(SessionRecord record, Duration ttl, boolean create) {
        return writeActiveOnce(record, ttl, create);
    }
    @Override public void writeRevoked(SessionRecord record) {
        writeRevokedOnce(record);
    }
}
