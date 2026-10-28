package com.abluepoint.summer.sample.systemparameter;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.abluepoint.summer.sample.cache.service.VersionedTwoLevelCache;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SystemParameterEventDeliveryServiceTest {
    @SuppressWarnings("unchecked")
    @Test
    void publishesContractWithoutValueAndMarksOutboxPublished() {
        var repository = mock(SystemParameterEventOutboxRepository.class);
        var redis = mock(StringRedisTemplate.class);
        var stream = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(stream);
        when(stream.add(any(MapRecord.class))).thenReturn(RecordId.of("1-0"));
        var cache = new VersionedTwoLevelCache(redis, new ObjectMapper(), java.time.Duration.ofMinutes(1), "test-node");
        var service = new SystemParameterEventDeliveryService(repository, redis, cache);
        var event = new SystemParameterEventOutboxEntity("event-publish", "ent-acme", "orders.page-size",
                "SYSTEM_PARAMETER_CHANGED", 4, "request-4", "{\"parameterKey\":\"orders.page-size\"}", Instant.now());

        service.publishOne(event, Instant.now());

        assertThat(event.status()).isEqualTo(SystemParameterEventStatus.PUBLISHED);
        verify(stream).add(any(MapRecord.class));
        verify(repository).saveAndFlush(event);
    }

    @SuppressWarnings("unchecked")
    @Test
    void failedRedisDeliveryUsesRetryThenDeadLetter() {
        var repository = mock(SystemParameterEventOutboxRepository.class);
        var redis = mock(StringRedisTemplate.class);
        var stream = mock(StreamOperations.class);
        when(redis.opsForStream()).thenReturn(stream);
        when(stream.add(any(MapRecord.class))).thenThrow(new IllegalStateException("redis unavailable"));
        var cache = new VersionedTwoLevelCache(redis, new ObjectMapper(), java.time.Duration.ofMinutes(1), "test-node");
        var service = new SystemParameterEventDeliveryService(repository, redis, cache);
        var event = new SystemParameterEventOutboxEntity("event-fail", "ent-acme", "orders.page-size",
                "SYSTEM_PARAMETER_CHANGED", 4, "request-5", "{}", Instant.now());

        service.publishOne(event, Instant.now());
        service.publishOne(event, Instant.now());
        service.publishOne(event, Instant.now());

        assertThat(event.status()).isEqualTo(SystemParameterEventStatus.DEAD_LETTER);
        assertThat(event.attempts()).isEqualTo(3);
        assertThat(event.lastError()).isEqualTo("redis_delivery_failed");
    }
}
