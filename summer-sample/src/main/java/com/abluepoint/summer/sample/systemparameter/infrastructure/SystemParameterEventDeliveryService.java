package com.abluepoint.summer.sample.systemparameter.infrastructure;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterRules;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterEventStatus;
import com.abluepoint.summer.sample.systemparameter.infrastructure.persistence.SystemParameterEventOutboxEntity;
import com.abluepoint.summer.sample.systemparameter.infrastructure.persistence.SystemParameterEventOutboxRepository;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.abluepoint.summer.sample.cache.service.VersionedTwoLevelCache;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemParameterEventDeliveryService {
    static final String STREAM = "summer:system-parameter-events";
    private final SystemParameterEventOutboxRepository repository;
    private final StringRedisTemplate redis;
    private final VersionedTwoLevelCache cache;

    public SystemParameterEventDeliveryService(SystemParameterEventOutboxRepository repository, StringRedisTemplate redis,
                                        VersionedTwoLevelCache cache) {
        this.repository = repository;
        this.redis = redis;
        this.cache = cache;
    }

    @Transactional
    public int publishDue(Instant now, int limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        List<SystemParameterEventOutboxEntity> due = repository
                .findTop100ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(SystemParameterEventStatus.PENDING, now)
                .stream().limit(limit).toList();
        due.forEach(event -> publishOne(event, now));
        return due.size();
    }

    @Transactional
    public void publishOne(SystemParameterEventOutboxEntity event, Instant now) {
        try {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("eventId", event.eventId());
            values.put("tenantId", event.tenantId());
            values.put("parameterKey", event.parameterKey());
            values.put("eventType", event.eventType());
            values.put("version", Long.toString(event.parameterVersion()));
            values.put("requestId", event.requestId() == null ? "" : event.requestId());
            MapRecord<String, String, String> record = StreamRecords.newRecord()
                    .ofMap(values).withStreamKey(STREAM);
            redis.opsForStream().add(record);
            cache.invalidate(event.tenantId(), "system-parameter");
            event.markPublished(now);
        } catch (RuntimeException exception) {
            if (SystemParameterRules.deadLetter(event.attempts())) event.markDeadLetter("redis_delivery_failed");
            else event.markRetry(now.plus(Duration.ofSeconds(SystemParameterRules.retrySeconds(event.attempts()))), "redis_delivery_failed");
        }
        repository.saveAndFlush(event);
    }
}
