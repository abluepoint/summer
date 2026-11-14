package com.abluepoint.summer.sample.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * A tenant-scoped cache with a local Caffeine tier and a Redis tier.
 *
 * <p>The Redis version is read for every lookup. An invalidation therefore
 * makes old L1 and L2 entries unreachable even when a node was disconnected
 * while the invalidation was published.</p>
 */
public final class VersionedTwoLevelCache {
    private static final Logger log = LoggerFactory.getLogger(VersionedTwoLevelCache.class);
    private static final String KEY_PREFIX = "summer:cache:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Cache<String, String> l1;
    private final Duration ttl;
    private final ConcurrentMap<String, Object> loadLocks = new ConcurrentHashMap<>();
    private final AtomicLong redisFailureCount = new AtomicLong();
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong localHits = new AtomicLong();
    private final AtomicLong remoteHits = new AtomicLong();
    private final AtomicLong loads = new AtomicLong();
    private final String nodeId;
    private final String keyPrefix;

    public VersionedTwoLevelCache(StringRedisTemplate redis, ObjectMapper objectMapper,
                                  Duration ttl, String nodeId) {
        this(redis, objectMapper, ttl, nodeId, "");
    }

    public VersionedTwoLevelCache(StringRedisTemplate redis, ObjectMapper objectMapper,
                                  Duration ttl, String nodeId, String namespace) {
        this.keyPrefix = namespace == null || namespace.isBlank()
                ? KEY_PREFIX : KEY_PREFIX + "namespace:" + encoded(namespace) + ":";
        this.redis = Objects.requireNonNull(redis, "redis");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        Duration safeTtl = ttl == null || ttl.isNegative() || ttl.isZero()
                ? Duration.ofMinutes(5) : ttl;
        this.ttl = safeTtl;
        this.l1 = Caffeine.newBuilder()
                .expireAfterWrite(safeTtl)
                .maximumSize(10_000)
                .build();
        this.nodeId = nodeId == null || nodeId.isBlank() ? "sample" : nodeId;
    }

    public <T> T get(String tenantId, String resource, String entry, Class<T> type, Supplier<T> loader) {
        Objects.requireNonNull(loader, "loader");
        String scope = scope(tenantId, resource);
        requests.incrementAndGet();
        String version;
        try {
            version = version(scope);
        } catch (RuntimeException failure) {
            recordRedisFailure(failure);
            l1.invalidateAll();
            return load(loader);
        }

        String entryKey = entryKey(scope, version, entry);
        String local = l1.getIfPresent(entryKey);
        if (local != null) { localHits.incrementAndGet(); return read(local, type); }

        Object lock = loadLocks.computeIfAbsent(entryKey, ignored -> new Object());
        try {
            synchronized (lock) {
                local = l1.getIfPresent(entryKey);
                if (local != null) { localHits.incrementAndGet(); return read(local, type); }
                String remote;
                try {
                    remote = redis.opsForValue().get(entryKey);
                } catch (RuntimeException failure) {
                    recordRedisFailure(failure);
                    l1.invalidateAll();
                    return load(loader);
                }
                if (remote != null) {
                    remoteHits.incrementAndGet();
                    l1.put(entryKey, remote);
                    return read(remote, type);
                }

                T loaded = load(loader);
                if (loaded != null) {
                    String encoded = write(loaded);
                    l1.put(entryKey, encoded);
                    try {
                        redis.opsForValue().set(entryKey, encoded, ttl);
                    } catch (RuntimeException failure) {
                        recordRedisFailure(failure);
                    }
                }
                return loaded;
            }
        } finally {
            loadLocks.remove(entryKey, lock);
        }
    }

    public void invalidate(String tenantId, String resource) {
        String scope = scope(tenantId, resource);
        evictLocal(scope);
        try {
            redis.opsForValue().increment(versionKey(scope));
        } catch (RuntimeException failure) {
            recordRedisFailure(failure);
        }
    }

    /** Evict this node without changing the shared Redis version. */
    public void evictLocal(String tenantId, String resource) {
        evictLocal(scope(tenantId, resource));
    }

    public long redisFailureCount() {
        return redisFailureCount.get();
    }

    /** Process-local, aggregate counters: no tenant, resource or entry keys. */
    public Statistics statistics() {
        return new Statistics(requests.get(), localHits.get(), remoteHits.get(), loads.get(), l1.estimatedSize());
    }

    public record Statistics(long requests, long localHits, long remoteHits, long loads, long localEntries) { }

    private <T> T load(Supplier<T> loader) {
        loads.incrementAndGet();
        return loader.get();
    }

    public String nodeId() {
        return nodeId;
    }

    private String version(String scope) {
        String key = versionKey(scope);
        String version = redis.opsForValue().get(key);
        if (version != null && !version.isBlank()) return version;
        redis.opsForValue().setIfAbsent(key, "0");
        String initialized = redis.opsForValue().get(key);
        return initialized == null || initialized.isBlank() ? "0" : initialized;
    }

    private void evictLocal(String scope) {
        String prefix = keyPrefix + "entry:" + encoded(scope) + ":";
        l1.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    private String versionKey(String scope) {
        return keyPrefix + "version:" + encoded(scope);
    }

    private String entryKey(String scope, String version, String entry) {
        return keyPrefix + "entry:" + encoded(scope) + ":" + encoded(version) + ":" + encoded(entry);
    }

    private static String scope(String tenantId, String resource) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (resource == null || resource.isBlank()) throw new IllegalArgumentException("resource is required");
        return tenantId + ":" + resource;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cache value cannot be serialized", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cache value cannot be read", exception);
        }
    }

    private void recordRedisFailure(RuntimeException failure) {
        redisFailureCount.incrementAndGet();
        log.warn("summer cache Redis operation unavailable; bypassing cached value");
    }

    private static String encoded(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
