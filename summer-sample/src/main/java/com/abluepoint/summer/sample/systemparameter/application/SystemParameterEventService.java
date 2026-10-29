package com.abluepoint.summer.sample.systemparameter.application;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterEventBody;

import com.abluepoint.summer.sample.systemparameter.application.view.SystemParameterEventPageView;
import com.abluepoint.summer.sample.systemparameter.application.view.SystemParameterEventView;
import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterEventNotFoundException;
import com.abluepoint.summer.sample.systemparameter.infrastructure.UuidV7;
import com.abluepoint.summer.sample.systemparameter.infrastructure.persistence.SystemParameterEntity;
import com.abluepoint.summer.sample.systemparameter.infrastructure.persistence.SystemParameterEventOutboxEntity;
import com.abluepoint.summer.sample.systemparameter.infrastructure.persistence.SystemParameterEventOutboxRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class SystemParameterEventService {
    private final SystemParameterEventOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public SystemParameterEventService(SystemParameterEventOutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueue(SystemParameterEntity parameter, String eventType, String requestId) {
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
    public SystemParameterEventPageView list(String tenant, String key) {
        List<SystemParameterEventOutboxEntity> rows = key == null || key.isBlank()
                ? repository.findAllByTenantIdOrderByCreatedAtDesc(tenant)
                : repository.findAllByTenantIdAndParameterKeyOrderByCreatedAtDesc(tenant, key);
        return new SystemParameterEventPageView(rows.stream().map(this::view).toList(), rows.size());
    }

    @Transactional
    public SystemParameterEventView retry(String tenant, String eventId) {
        SystemParameterEventOutboxEntity entity = repository.findById(eventId)
                .orElseThrow(() -> new SystemParameterEventNotFoundException(eventId));
        if (!tenant.equals(entity.tenantId())) {
            throw new SystemParameterEventNotFoundException(eventId);
        }
        entity.resetForRetry(Instant.now());
        return view(repository.saveAndFlush(entity));
    }

    public SystemParameterEventView view(SystemParameterEventOutboxEntity entity) {
        return new SystemParameterEventView(entity.eventId(), entity.tenantId(), entity.parameterKey(),
                entity.eventType(), entity.parameterVersion(), entity.requestId(), entity.status(),
                entity.attempts(), entity.nextAttemptAt(), entity.lastError(), entity.createdAt(),
                entity.updatedAt(), new SystemParameterEventBody(entity.eventId(), entity.tenantId(),
                entity.parameterKey(), entity.eventType(), entity.parameterVersion(), entity.requestId()));
    }
}
