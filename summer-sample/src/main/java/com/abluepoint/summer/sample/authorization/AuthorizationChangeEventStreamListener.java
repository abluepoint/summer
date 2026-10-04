package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.cache.VersionedTwoLevelCache;
import com.abluepoint.summer.sample.cache.VersionedTwoLevelCacheProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "summer.sample.authorization-events.scheduling-enabled", havingValue = "true")
class AuthorizationChangeEventStreamListener {
    private static final String GROUP_PREFIX = "summer-authorization-consumers-";
    private final StringRedisTemplate redis;
    private final AuthorizationChangeEventConsumer consumer;
    private final AuthorizationService authorizationService;
    private final VersionedTwoLevelCache cache;
    private final String group;
    private final String consumerName;

    AuthorizationChangeEventStreamListener(StringRedisTemplate redis, AuthorizationChangeEventConsumer consumer,
                                           AuthorizationService authorizationService, VersionedTwoLevelCache cache,
                                           VersionedTwoLevelCacheProperties properties) {
        this.redis = redis; this.consumer = consumer; this.authorizationService = authorizationService; this.cache = cache;
        this.consumerName = safeName(properties.getNodeId()); this.group = GROUP_PREFIX + consumerName;
    }

    @Scheduled(fixedDelayString = "${summer.sample.authorization-events.poll-interval-ms:5000}")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void poll() {
        try {
            ensureGroup();
            List<?> records = redis.opsForStream().read(Consumer.from(group, consumerName),
                    StreamOffset.create(AuthorizationChangeEventService.STREAM, ReadOffset.lastConsumed()));
            for (Object value : records) {
                MapRecord<?, ?, ?> record = (MapRecord<?, ?, ?>) value;
                AuthorizationChangeEventBody event = toEvent(record);
                // Every node evicts its own tier even when the shared receipt says another node
                // already applied the event. This is what makes a shared receipt safe with node groups.
                cache.evictLocal(event.enterpriseId(), "authorization");
                AuthorizationChangeEventConsumeResult result = consumer.consume(event, ignored -> authorizationService.reloadPersistentState());
                if (result == AuthorizationChangeEventConsumeResult.DUPLICATE) authorizationService.reloadPersistentState();
                if (result == AuthorizationChangeEventConsumeResult.PROCESSED
                        || result == AuthorizationChangeEventConsumeResult.DUPLICATE
                        || result == AuthorizationChangeEventConsumeResult.DEAD_LETTER) {
                    redis.opsForStream().acknowledge(AuthorizationChangeEventService.STREAM, group, record.getId());
                }
            }
        } catch (RuntimeException ignored) {
            // Leave records pending and let the outbox retry path recover publication failures.
        }
    }

    private void ensureGroup() {
        try { redis.opsForStream().createGroup(AuthorizationChangeEventService.STREAM, ReadOffset.latest(), group); }
        catch (RuntimeException ignored) { /* BUSYGROUP */ }
    }

    private static AuthorizationChangeEventBody toEvent(MapRecord<?, ?, ?> record) {
        Map<?, ?> value = record.getValue();
        return new AuthorizationChangeEventBody(string(value.get("eventId")), string(value.get("enterpriseId")),
                string(value.get("resourceType")), string(value.get("resourceId")), string(value.get("eventType")),
                Long.parseLong(string(value.get("version")) == null ? "0" : string(value.get("version"))), string(value.get("requestId")));
    }
    private static String string(Object value) { return value == null ? null : value.toString(); }
    private static String safeName(String value) { return value == null || value.isBlank() ? "summer-sample" : value.replaceAll("[^A-Za-z0-9_-]", "-"); }
}
