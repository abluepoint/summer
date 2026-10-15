package com.abluepoint.summer.sample.systemparameter;

import com.abluepoint.summer.sample.cache.VersionedTwoLevelCache;
import com.abluepoint.summer.sample.cache.VersionedTwoLevelCacheProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "summer.sample.parameter-events.scheduling-enabled", havingValue = "true")
class SystemParameterEventStreamListener {
    private static final String GROUP_PREFIX = "summer-system-parameter-consumers-";
    private static final int BATCH_SIZE = 100;
    private static final Duration CLAIM_IDLE = Duration.ofSeconds(30);
    private final StringRedisTemplate redis;
    private final SystemParameterEventConsumer consumer;
    private final VersionedTwoLevelCache cache;
    private final String group;
    private final String consumerName;

    SystemParameterEventStreamListener(StringRedisTemplate redis, SystemParameterEventConsumer consumer,
                                       VersionedTwoLevelCache cache, VersionedTwoLevelCacheProperties properties) {
        this.redis = redis;
        this.consumer = consumer;
        this.cache = cache;
        this.consumerName = safeName(properties.getNodeId());
        this.group = GROUP_PREFIX + consumerName;
    }

    @Scheduled(fixedDelayString = "${summer.sample.parameter-events.poll-interval-ms:5000}")
    void poll() {
        try {
            ensureGroup();
            // '>' only returns new entries; Redis does not redeliver the pending list by itself.
            process(redis.<String, String>opsForStream().read(
                    Consumer.from(group, consumerName),
                    StreamReadOptions.empty().count(BATCH_SIZE),
                    StreamOffset.create(SystemParameterEventDeliveryService.STREAM, ReadOffset.from("0"))));
            claimIdlePending();
            process(redis.<String, String>opsForStream().read(
                    Consumer.from(group, consumerName),
                    StreamReadOptions.empty().count(BATCH_SIZE),
                    StreamOffset.create(SystemParameterEventDeliveryService.STREAM, ReadOffset.lastConsumed())));
        } catch (RuntimeException ignored) {
            // Redis/group failures are retried by the next scheduled poll.
        }
    }

    private void claimIdlePending() {
        List<RecordId> abandoned = new ArrayList<>();
        var pending = redis.opsForStream().pending(SystemParameterEventDeliveryService.STREAM,
                group, Range.unbounded(), BATCH_SIZE, CLAIM_IDLE);
        for (var message : pending) {
            if (!consumerName.equals(message.getConsumerName())) abandoned.add(message.getId());
        }
        if (!abandoned.isEmpty()) {
            process(redis.<String, String>opsForStream().claim(SystemParameterEventDeliveryService.STREAM,
                    group, consumerName, CLAIM_IDLE, abandoned.toArray(RecordId[]::new)));
        }
    }

    private void process(List<? extends MapRecord<?, ?, ?>> records) {
        for (var record : records) {
            try {
                SystemParameterEventBody event = toEvent(record);
                // Receipts are shared for delivery idempotency, while each node has its own
                // stream group. Evict before the receipt check so every node invalidates its L1.
                cache.evictLocal(event.tenantId(), "system-parameter");
                SystemParameterEventConsumeResult result = consumer.consume(event, ignored -> { });
                if (result == SystemParameterEventConsumeResult.PROCESSED
                        || result == SystemParameterEventConsumeResult.DUPLICATE
                        || result == SystemParameterEventConsumeResult.DEAD_LETTER) {
                    redis.opsForStream().acknowledge(SystemParameterEventDeliveryService.STREAM, group, record.getId());
                }
            } catch (RuntimeException ignored) {
                // Keep this entry pending without preventing other entries from progressing.
            }
        }
    }

    private void ensureGroup() {
        try {
            redis.opsForStream().createGroup(SystemParameterEventDeliveryService.STREAM,
                    ReadOffset.from("0-0"), group);
        } catch (RuntimeException ignored) {
            // BUSYGROUP means another scheduler instance already created it.
        }
    }

    private static SystemParameterEventBody toEvent(MapRecord<?, ?, ?> record) {
        Map<?, ?> value = record.getValue();
        return new SystemParameterEventBody(string(value.get("eventId")), string(value.get("tenantId")),
                string(value.get("parameterKey")), string(value.get("eventType")),
                Long.parseLong(string(value.get("version")) == null ? "0" : string(value.get("version"))), string(value.get("requestId")));
    }

    private static String string(Object value) { return value == null ? null : value.toString(); }

    private static String safeName(String value) {
        if (value == null || value.isBlank()) return "summer-sample";
        return value.replaceAll("[^A-Za-z0-9_-]", "-");
    }
}
