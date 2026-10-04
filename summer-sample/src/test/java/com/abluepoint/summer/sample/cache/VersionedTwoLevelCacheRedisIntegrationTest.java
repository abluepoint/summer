package com.abluepoint.summer.sample.cache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "summer.sample.parameter-events.scheduling-enabled=false",
        "summer.security.storage.keys=memory"
})
@Testcontainers(disabledWithoutDocker = true)
class VersionedTwoLevelCacheRedisIntegrationTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper objectMapper;

    @Test
    void twoNodesShareL2AndVersionInvalidationMakesOldEntriesUnreachable() {
        VersionedTwoLevelCache nodeA = new VersionedTwoLevelCache(
                redis, objectMapper, Duration.ofMinutes(1), "node-a");
        VersionedTwoLevelCache nodeB = new VersionedTwoLevelCache(
                redis, objectMapper, Duration.ofMinutes(1), "node-b");
        AtomicInteger nodeBLoads = new AtomicInteger();

        assertThat(nodeA.get("ent-acme", "dictionary", "customer-segment", String.class,
                () -> "old")).isEqualTo("old");
        assertThat(nodeB.get("ent-acme", "dictionary", "customer-segment", String.class,
                () -> {
                    nodeBLoads.incrementAndGet();
                    return "unexpected";
                })).isEqualTo("old");
        assertThat(nodeBLoads).hasValue(0);

        nodeA.invalidate("ent-acme", "dictionary");

        assertThat(nodeB.get("ent-acme", "dictionary", "customer-segment", String.class,
                () -> {
                    nodeBLoads.incrementAndGet();
                    return "new";
                })).isEqualTo("new");
        assertThat(nodeBLoads).hasValue(1);
    }

    @Test
    void l1AndL2ExpireAfterConfiguredTtl() throws InterruptedException {
        VersionedTwoLevelCache cache = new VersionedTwoLevelCache(
                redis, objectMapper, Duration.ofMillis(50), "ttl-node");
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache.get("ent-acme", "system-parameter", "short-lived", String.class,
                () -> "value-" + loads.incrementAndGet())).isEqualTo("value-1");
        Thread.sleep(120);
        assertThat(cache.get("ent-acme", "system-parameter", "short-lived", String.class,
                () -> "value-" + loads.incrementAndGet())).isEqualTo("value-2");
        assertThat(loads).hasValue(2);
    }
}
