package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.cache.VersionedTwoLevelCache;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
class AuthorizationChangeEventDeliveryService {
    private static final int MAX_ATTEMPTS = 3;
    private final AuthorizationChangeEventOutboxRepository repository;
    private final StringRedisTemplate redis;
    private final VersionedTwoLevelCache cache;

    AuthorizationChangeEventDeliveryService(AuthorizationChangeEventOutboxRepository repository, StringRedisTemplate redis,
                                             VersionedTwoLevelCache cache) {
        this.repository = repository; this.redis = redis; this.cache = cache;
    }

    @Transactional
    int publishDue(Instant now, int limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        List<AuthorizationChangeEventOutboxEntity> due = repository.findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                AuthorizationChangeEventStatus.PENDING, now).stream().limit(limit).toList();
        due.forEach(event -> publishOne(event, now));
        return due.size();
    }

    @Transactional
    void publishOne(AuthorizationChangeEventOutboxEntity event, Instant now) {
        try {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("eventId", event.eventId()); values.put("enterpriseId", event.enterpriseId());
            values.put("resourceType", event.resourceType()); values.put("resourceId", event.resourceId());
            values.put("eventType", event.eventType()); values.put("version", Long.toString(event.resourceVersion()));
            values.put("requestId", event.requestId() == null ? "" : event.requestId());
            redis.opsForStream().add(StreamRecords.newRecord().ofMap(values).withStreamKey(AuthorizationChangeEventService.STREAM));
            cache.invalidate(event.enterpriseId(), "authorization");
            event.markPublished(now);
        } catch (RuntimeException exception) {
            if (event.attempts() + 1 >= MAX_ATTEMPTS) event.markDeadLetter("redis_delivery_failed");
            else event.markRetry(now.plus(Duration.ofSeconds(5L << event.attempts())), "redis_delivery_failed");
        }
        repository.saveAndFlush(event);
    }
}
