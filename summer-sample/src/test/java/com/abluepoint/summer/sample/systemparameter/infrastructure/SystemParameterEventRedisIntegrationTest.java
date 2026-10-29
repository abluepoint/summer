package com.abluepoint.summer.sample.systemparameter.infrastructure;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterEventStatus;
import com.abluepoint.summer.sample.systemparameter.infrastructure.SystemParameterEventDeliveryService;
import com.abluepoint.summer.sample.systemparameter.infrastructure.persistence.SystemParameterEventOutboxEntity;
import com.abluepoint.summer.sample.systemparameter.infrastructure.persistence.SystemParameterEventOutboxRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "summer.sample.parameter-events.scheduling-enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class SystemParameterEventRedisIntegrationTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired SystemParameterEventOutboxRepository repository;
    @Autowired SystemParameterEventDeliveryService delivery;
    @Autowired StringRedisTemplate redis;

    @Test
    void publishesPendingOutboxToRedisStream() {
        var event = new SystemParameterEventOutboxEntity("redis-event", "ent-acme", "orders.page-size",
                "SYSTEM_PARAMETER_CHANGED", 7, "redis-request", "{\"parameterKey\":\"orders.page-size\"}", Instant.now());
        repository.saveAndFlush(event);

        assertThat(delivery.publishDue(Instant.now(), 10)).isEqualTo(1);
        assertThat(repository.findById("redis-event").orElseThrow().status())
                .isEqualTo(SystemParameterEventStatus.PUBLISHED);
        assertThat(redis.opsForStream().size(SystemParameterEventDeliveryService.STREAM)).isEqualTo(1L);
        var records = redis.opsForStream().range(SystemParameterEventDeliveryService.STREAM,
                org.springframework.data.domain.Range.unbounded());
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.getValue()).containsEntry("parameterKey", "orders.page-size");
            assertThat(record.getValue()).doesNotContainKey("value");
        });
    }
}
