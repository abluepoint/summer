package com.abluepoint.summer.sample.systemparameter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
class SystemParameterEventService {
    private final SystemParameterEventOutboxRepository repository;
    private final ObjectMapper objectMapper;

    SystemParameterEventService(SystemParameterEventOutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    void enqueue(SystemParameterEntity parameter, String eventType, String requestId) {
        Instant now = Instant.now();
        String eventId = UuidV7.next();
        SystemParameterEventBody body = new SystemParameterEventBody(eventId, parameter.tenantId(),
                parameter.key(), eventType, parameter.version(), requestId);
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventId", body.eventId(), "tenantId", body.tenantId(),
                    "parameterKey", body.parameterKey(), "eventType", body.eventType(),
                    "version", body.version(), "requestId", body.requestId() == null ? "" : body.requestId()));
            repository.saveAndFlush(new SystemParameterEventOutboxEntity(eventId, parameter.tenantId(),
                    parameter.key(), eventType, parameter.version(), requestId, payload, now));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("System parameter event cannot be serialized", exception);
        }
    }

    @Transactional(readOnly = true)
    SystemParameterEventPage list(String tenant, String key) {
        List<SystemParameterEventOutboxEntity> rows = key == null || key.isBlank()
                ? repository.findAllByTenantIdOrderByCreatedAtDesc(tenant)
                : repository.findAllByTenantIdAndParameterKeyOrderByCreatedAtDesc(tenant, key);
        return new SystemParameterEventPage(rows.stream().map(this::view).toList(), rows.size());
    }

    @Transactional
    SystemParameterEventView retry(String tenant, String eventId) {
        SystemParameterEventOutboxEntity entity = repository.findById(eventId)
                .orElseThrow(() -> new SystemParameterEventNotFoundException(eventId));
        if (!tenant.equals(entity.tenantId())) {
            throw new SystemParameterEventNotFoundException(eventId);
        }
        entity.resetForRetry(Instant.now());
        return view(repository.saveAndFlush(entity));
    }

    SystemParameterEventView view(SystemParameterEventOutboxEntity entity) {
        return new SystemParameterEventView(entity.eventId(), entity.tenantId(), entity.parameterKey(),
                entity.eventType(), entity.parameterVersion(), entity.requestId(), entity.status(),
                entity.attempts(), entity.nextAttemptAt(), entity.lastError(), entity.createdAt(),
                entity.updatedAt(), new SystemParameterEventBody(entity.eventId(), entity.tenantId(),
                entity.parameterKey(), entity.eventType(), entity.parameterVersion(), entity.requestId()));
    }
}

final class SystemParameterEventNotFoundException extends RuntimeException {
    SystemParameterEventNotFoundException(String eventId) { super("System parameter event not found: " + eventId); }
}
