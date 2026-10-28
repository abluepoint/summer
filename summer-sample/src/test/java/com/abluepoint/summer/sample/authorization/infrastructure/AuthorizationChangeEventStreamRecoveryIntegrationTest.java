package com.abluepoint.summer.sample.authorization.infrastructure;

import com.abluepoint.summer.sample.authorization.infrastructure.AuthorizationChangeEventConsumer;
import com.abluepoint.summer.sample.authorization.application.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.authorization.infrastructure.AuthorizationChangeReceiptStatus;
import com.abluepoint.summer.sample.authorization.application.AuthorizationService;
import com.abluepoint.summer.sample.authorization.infrastructure.AuthorizationChangeEventStreamListener;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationChangeEventReceiptRepository;

import com.abluepoint.summer.sample.cache.service.VersionedTwoLevelCache;
import com.abluepoint.summer.sample.cache.configuration.VersionedTwoLevelCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Redis stream groups and persisted receipts; native MySQL evidence is separate. */
@SpringBootTest(properties = "summer.sample.authorization-events.scheduling-enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class AuthorizationChangeEventStreamRecoveryIntegrationTest {
    private static final String STREAM = AuthorizationChangeEventService.STREAM;
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired StringRedisTemplate redis;
    @Autowired AuthorizationChangeEventConsumer consumer;
    @Autowired AuthorizationChangeEventReceiptRepository receipts;
    @Autowired AuthorizationService authorization;
    @Autowired VersionedTwoLevelCache cache;

    @BeforeEach
    void cleanOwnFixture() {
        redis.delete(STREAM);
        receipts.deleteAll();
    }

    @Test
    void joiningNodeConsumesAnEventPublishedBeforeItsGroupExists() {
        String event = append();
        listener("joining-node").poll();
        assertThat(receipts.findById(event)).isPresent();
    }

    @Test
    void recreatedGroupConsumesFirstPostRestartPublication() {
        var listener = listener("stable-node");
        listener.poll();
        // Redis without persistence loses both the stream and its groups.
        redis.delete(STREAM);
        String event = append();
        listener.poll();
        assertThat(receipts.findById(event)).isPresent();
    }

    @Test
    void duplicatePublicationIsAcknowledgedWithoutAnotherReceipt() {
        var listener = listener("stable-node");
        listener.poll();
        String event = append();
        listener.poll();
        append(event);
        listener.poll();
        assertThat(receipts.count()).isEqualTo(1);
        assertThat(receipts.findById(event).orElseThrow().status()).isEqualTo(AuthorizationChangeReceiptStatus.PROCESSED);
        assertThat(redis.opsForStream().pending(STREAM, "summer-authorization-consumers-stable-node")
                .getTotalPendingMessages()).isZero();
    }

    private AuthorizationChangeEventStreamListener listener(String node) {
        var properties = new VersionedTwoLevelCacheProperties();
        properties.setNodeId(node);
        return new AuthorizationChangeEventStreamListener(redis, consumer, authorization, cache, properties);
    }

    private String append() {
        String event = UUID.randomUUID().toString();
        append(event);
        return event;
    }

    private void append(String event) {
        redis.opsForStream().add(STREAM, Map.of("eventId", event, "enterpriseId", "ent-acme",
                "resourceType", "ROLE", "resourceId", "recovery-role", "eventType", "ROLE_UPDATED",
                "version", "1", "requestId", "recovery-request"));
    }
}
