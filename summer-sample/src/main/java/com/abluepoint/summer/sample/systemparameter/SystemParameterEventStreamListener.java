package com.abluepoint.summer.sample.systemparameter;

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
@ConditionalOnProperty(name = "summer.sample.parameter-events.scheduling-enabled", havingValue = "true")
class SystemParameterEventStreamListener {
    private static final String GROUP = "summer-system-parameter-consumers";
    private static final String CONSUMER = "summer-sample";
    private final StringRedisTemplate redis;
    private final SystemParameterEventConsumer consumer;

    SystemParameterEventStreamListener(StringRedisTemplate redis, SystemParameterEventConsumer consumer) {
        this.redis = redis;
        this.consumer = consumer;
    }

    @Scheduled(fixedDelayString = "${summer.sample.parameter-events.poll-interval-ms:5000}")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void poll() {
        try {
            ensureGroup();
            List<?> records = redis.opsForStream().read(
                    Consumer.from(GROUP, CONSUMER),
                    StreamOffset.create(SystemParameterEventDeliveryService.STREAM, ReadOffset.lastConsumed()));
            for (Object value : records) {
                MapRecord<?, ?, ?> record = (MapRecord<?, ?, ?>) value;
                SystemParameterEventConsumeResult result = consumer.consume(toEvent(record), ignored -> {
                    // The sample has no local parameter cache yet; the receipt still makes delivery idempotent.
                });
                if (result == SystemParameterEventConsumeResult.PROCESSED
                        || result == SystemParameterEventConsumeResult.DUPLICATE) {
                    redis.opsForStream().acknowledge(SystemParameterEventDeliveryService.STREAM, GROUP, record.getId());
                }
            }
        } catch (RuntimeException ignored) {
            // Leave unacknowledged entries for Redis redelivery; diagnostics remain in the outbox status.
        }
    }

    private void ensureGroup() {
        try {
            redis.opsForStream().createGroup(SystemParameterEventDeliveryService.STREAM,
                    ReadOffset.latest(), GROUP);
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
}
