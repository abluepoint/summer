package com.abluepoint.summer.sample.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class VersionedTwoLevelCacheTest {

    @Test
    void readsFromL1AfterTheFirstLoadWithinTheSameVersion() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(versionKey("ent-acme", "system-parameter"))).thenReturn("7");

        VersionedTwoLevelCache cache = new VersionedTwoLevelCache(
                redis, new ObjectMapper(), Duration.ofMinutes(1), "node-a");
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache.get("ent-acme", "system-parameter", "orders.page-size", String.class,
                () -> "value-" + loads.incrementAndGet())).isEqualTo("value-1");
        assertThat(cache.get("ent-acme", "system-parameter", "orders.page-size", String.class,
                () -> "value-" + loads.incrementAndGet())).isEqualTo("value-1");
        assertThat(loads).hasValue(1);
    }

    @Test
    void protectsAColdEntryFromAConcurrentCacheStampede() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(versionKey("ent-acme", "dictionary"))).thenReturn("2");

        VersionedTwoLevelCache cache = new VersionedTwoLevelCache(
                redis, new ObjectMapper(), Duration.ofMinutes(1), "node-a");
        AtomicInteger loads = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            for (int index = 0; index < 8; index++) {
                workers.submit(() -> cache.get("ent-acme", "dictionary", "customer-segment",
                        String.class, () -> {
                            loads.incrementAndGet();
                            try {
                                Thread.sleep(25);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            }
                            return "ready";
                        }));
            }
        } finally {
            workers.shutdown();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(loads).hasValue(1);
    }

    @Test
    void bypassesLocalAndRemoteEntriesWhenRedisCannotValidateTheVersion() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(versionKey("ent-acme", "system-parameter")))
                .thenReturn("1")
                .thenThrow(new IllegalStateException("redis unavailable"));

        VersionedTwoLevelCache cache = new VersionedTwoLevelCache(
                redis, new ObjectMapper(), Duration.ofMinutes(1), "node-a");
        assertThat(cache.get("ent-acme", "system-parameter", "secret.value", String.class,
                () -> "old")).isEqualTo("old");
        doThrow(new IllegalStateException("redis unavailable"))
                .when(values).get(versionKey("ent-acme", "system-parameter"));
        assertThat(cache.get("ent-acme", "system-parameter", "secret.value", String.class,
                () -> "fresh")).isEqualTo("fresh");
        assertThat(cache.redisFailureCount()).isEqualTo(1);
    }

    private static String versionKey(String tenant, String resource) {
        String scope = tenant + ":" + resource;
        return "summer:cache:version:" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(scope.getBytes(StandardCharsets.UTF_8));
    }
}
