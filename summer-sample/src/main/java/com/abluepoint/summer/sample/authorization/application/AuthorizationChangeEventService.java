package com.abluepoint.summer.sample.authorization.application;

import com.abluepoint.summer.sample.authorization.infrastructure.AuthorizationChangeReceiptStatus;

import com.abluepoint.summer.sample.authorization.application.view.AuthorizationChangeEventPageView;
import com.abluepoint.summer.sample.authorization.application.view.AuthorizationChangeEventView;
import com.abluepoint.summer.sample.authorization.domain.exception.AuthorizationChangeEventNotFoundException;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationChangeEventOutboxEntity;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationChangeEventOutboxRepository;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationChangeEventReceiptRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@org.springframework.modulith.NamedInterface("changes")
public class AuthorizationChangeEventService {
    public static final String STREAM = "summer:authorization-change-events";
    private final AuthorizationChangeEventOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final AuthorizationChangeEventReceiptRepository receipts;

    public AuthorizationChangeEventService(AuthorizationChangeEventOutboxRepository repository, ObjectMapper objectMapper, AuthorizationChangeEventReceiptRepository receipts) {
        this.repository = repository; this.objectMapper = objectMapper; this.receipts = receipts;
    }

    @Transactional
    public void enqueue(String enterpriseId, String resourceType, String resourceId, String eventType,
                        long version, String requestId) {
        if (enterpriseId == null || enterpriseId.isBlank()) throw new IllegalArgumentException("enterpriseId is required");
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        AuthorizationChangeEvent body = new AuthorizationChangeEvent(eventId, enterpriseId, resourceType,
                resourceId, eventType, version, requestId);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventId", eventId); payload.put("enterpriseId", enterpriseId);
            payload.put("resourceType", resourceType); payload.put("resourceId", resourceId);
            payload.put("eventType", eventType); payload.put("version", version);
            payload.put("requestId", requestId == null ? "" : requestId);
            repository.save(new AuthorizationChangeEventOutboxEntity(eventId, enterpriseId, resourceType, resourceId,
                    eventType, version, requestId, objectMapper.writeValueAsString(payload), now));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Authorization change event cannot be serialized", exception);
        }
    }

    @Transactional(readOnly = true)
    public AuthorizationChangeEventPageView list(String enterpriseId, String resourceType) {
        List<AuthorizationChangeEventOutboxEntity> rows = resourceType == null || resourceType.isBlank()
                ? repository.findAllByEnterpriseIdOrderByCreatedAtDesc(enterpriseId)
                : repository.findAllByEnterpriseIdAndResourceTypeOrderByCreatedAtDesc(enterpriseId, resourceType);
        return new AuthorizationChangeEventPageView(rows.stream().map(this::view).toList(), rows.size());
    }

    @Transactional
    public AuthorizationChangeEventView retry(String enterpriseId, String eventId) {
        AuthorizationChangeEventOutboxEntity entity = repository.findById(eventId)
                .filter(row -> enterpriseId.equals(row.enterpriseId()))
                .orElseThrow(() -> new AuthorizationChangeEventNotFoundException(eventId));
        receipts.findById(eventId).filter(receipt -> receipt.status() == AuthorizationChangeReceiptStatus.DEAD_LETTER)
                .ifPresent(receipts::delete);
        entity.resetForRetry(Instant.now());
        return view(repository.saveAndFlush(entity));
    }

    public AuthorizationChangeEventView view(AuthorizationChangeEventOutboxEntity entity) {
        Map<String, Object> payload;
        try { payload = objectMapper.readValue(entity.payloadJson(), Map.class); }
        catch (RuntimeException ignored) { payload = Map.of(); }
        return new AuthorizationChangeEventView(entity.eventId(), entity.enterpriseId(), entity.resourceType(),
                entity.resourceId(), entity.eventType(), entity.resourceVersion(), entity.requestId(), entity.status(),
                entity.attempts(), entity.nextAttemptAt(), entity.lastError(), entity.createdAt(), entity.updatedAt(), payload);
    }
}
