package com.abluepoint.summer.sample.systemparameter;

import com.abluepoint.summer.sample.cache.VersionedTwoLevelCache;
import com.abluepoint.summer.sample.cache.VersionedTwoLevelCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Redis protocol and persisted receipts; native MySQL/HTTP/Chrome evidence is separate. */
@SpringBootTest(properties = "summer.sample.parameter-events.scheduling-enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class SystemParameterEventStreamRecoveryIntegrationTest {
    private static final String STREAM = SystemParameterEventDeliveryService.STREAM;
    private static final String NODE = "recovery-node";
    private static final String GROUP = "summer-system-parameter-consumers-" + NODE;
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired StringRedisTemplate redis;
    @Autowired SystemParameterEventConsumer consumer;
    @Autowired SystemParameterEventReceiptRepository receipts;
    @Autowired VersionedTwoLevelCache cache;

    @BeforeEach
    void cleanOwnFixture() {
        command("ACL", "SETUSER", "default", "+xack");
        redis.delete(STREAM);
        receipts.deleteAll();
    }

    @Test
    void retriesOwnPendingAfterAcknowledgementFailureAndListenerRestart() {
        var listener = listener(NODE, consumer);
        listener.poll();
        String eventId = append();
        command("ACL", "SETUSER", "default", "-xack");
        try {
            listener.poll();
            assertThat(receipts.findById(eventId)).isPresent();
            assertThat(redis.opsForStream().pending(STREAM, GROUP).getTotalPendingMessages()).isEqualTo(1);
        } finally {
            command("ACL", "SETUSER", "default", "+xack");
        }
        listener(NODE, consumer).poll();
        assertThat(redis.opsForStream().pending(STREAM, GROUP).getTotalPendingMessages()).isZero();
    }

    @Test
    void claimsIdlePendingOwnedByRetiredConsumer() {
        var listener = listener(NODE, consumer);
        listener.poll();
        String eventId = append();
        var delivered = redis.opsForStream().read(Consumer.from(GROUP, "retired-process"),
                StreamOffset.create(STREAM, ReadOffset.lastConsumed()));
        String recordId = delivered.getFirst().getId().getValue();
        command("XCLAIM", STREAM, GROUP, "retired-process", "0", recordId, "IDLE", "31000");
        listener.poll();
        assertThat(receipts.findById(eventId)).isPresent();
        assertThat(redis.opsForStream().pending(STREAM, GROUP).getTotalPendingMessages()).isZero();
    }

    @Test
    void joiningNodeConsumesUnprocessedStreamBacklog() {
        listener(NODE, consumer).poll();
        String eventId = append();
        listener("joining-node", consumer).poll();
        assertThat(receipts.findById(eventId)).isPresent();
    }

    @Test
    void retriesFailingHandlerToPersistedDeadLetterAndAcknowledgesTerminalEntry() {
        var failing = new SystemParameterEventConsumer(receipts) {
            @Override
            SystemParameterEventConsumeResult consume(SystemParameterEventBody event,
                                                       java.util.function.Consumer<SystemParameterEventBody> ignored) {
                return consumer.consume(event, body -> { throw new IllegalStateException("controlled_handler_failure"); });
            }
        };
        var listener = listener(NODE, failing);
        listener.poll();
        String eventId = append();
        listener.poll();
        listener.poll();
        listener.poll();
        var receipt = receipts.findById(eventId).orElseThrow();
        assertThat(receipt.status()).isEqualTo(SystemParameterEventReceiptStatus.DEAD_LETTER);
        assertThat(receipt.attempts()).isEqualTo(3);
        assertThat(redis.opsForStream().pending(STREAM, GROUP).getTotalPendingMessages()).isZero();
    }

    @Test
    void duplicateStreamRecordsInvokeRealHandlerOnlyOnce() {
        AtomicInteger calls = new AtomicInteger();
        var counting = new SystemParameterEventConsumer(receipts) {
            @Override
            SystemParameterEventConsumeResult consume(SystemParameterEventBody event,
                                                       java.util.function.Consumer<SystemParameterEventBody> ignored) {
                return consumer.consume(event, body -> calls.incrementAndGet());
            }
        };
        var listener = listener(NODE, counting);
        listener.poll();
        String eventId = append();
        append(eventId);
        listener.poll();
        assertThat(calls).hasValue(1);
        assertThat(receipts.count()).isEqualTo(1);
        assertThat(redis.opsForStream().pending(STREAM, GROUP).getTotalPendingMessages()).isZero();
    }

    @Test
    void deadLetterReceiptRemainsTerminalOnDuplicateAndJoiningNodeReplay() {
        listener(NODE, consumer).poll();
        String eventId = append();
        var event = new SystemParameterEventBody(eventId, "ent-acme", "recovery.fixture",
                "SYSTEM_PARAMETER_CHANGED", 0, "recovery-request");
        for (int attempt = 0; attempt < 3; attempt++) {
            consumer.consume(event, body -> { throw new IllegalStateException("controlled_handler_failure"); });
        }
        AtomicInteger calls = new AtomicInteger();
        assertThat(consumer.consume(event, body -> calls.incrementAndGet()))
                .isEqualTo(SystemParameterEventConsumeResult.DEAD_LETTER);
        assertThat(calls).hasValue(0);
        listener("joining-node", consumer).poll();
        assertThat(receipts.findById(eventId).orElseThrow().status())
                .isEqualTo(SystemParameterEventReceiptStatus.DEAD_LETTER);
    }

    private SystemParameterEventStreamListener listener(String node, SystemParameterEventConsumer handler) {
        var properties = new VersionedTwoLevelCacheProperties();
        properties.setNodeId(node);
        return new SystemParameterEventStreamListener(redis, handler, cache, properties);
    }

    private String append() {
        String eventId = UUID.randomUUID().toString();
        append(eventId);
        return eventId;
    }

    private void append(String eventId) {
        redis.opsForStream().add(STREAM, Map.of("eventId", eventId, "tenantId", "ent-acme",
                "parameterKey", "recovery.fixture", "eventType", "SYSTEM_PARAMETER_CHANGED",
                "version", "0", "requestId", "recovery-request"));
    }

    private void command(String... args) {
        byte[][] values = java.util.Arrays.stream(args).skip(1)
                .map(value -> value.getBytes(StandardCharsets.UTF_8)).toArray(byte[][]::new);
        redis.execute((RedisCallback<Object>) connection -> connection.execute(args[0], values));
    }
}
