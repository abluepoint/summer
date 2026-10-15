package com.abluepoint.summer.sample.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CacheObservationTest {
    @Test
    @SuppressWarnings("unchecked")
    void aggregateStatisticsCountHitsAndBypassWithoutChangingCacheBehavior() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(startsWith("summer:cache:version:"))).thenReturn("1");
        var cache = new VersionedTwoLevelCache(redis, new ObjectMapper(), Duration.ofMinutes(1), "observation");
        assertThat(cache.get("tenant", "resource", "entry", String.class, () -> "loaded")).isEqualTo("loaded");
        assertThat(cache.get("tenant", "resource", "entry", String.class, () -> "unexpected")).isEqualTo("loaded");
        when(values.get(startsWith("summer:cache:version:"))).thenThrow(new IllegalStateException());
        assertThat(cache.get("tenant", "resource", "entry", String.class, () -> "fresh")).isEqualTo("fresh");
        assertThat(cache.statistics().requests()).isEqualTo(3);
        assertThat(cache.statistics().localHits()).isEqualTo(1);
        assertThat(cache.statistics().loads()).isEqualTo(2);
        assertThat(cache.redisFailureCount()).isEqualTo(1);
    }
}
