package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.error.CommonErrorCode;

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
public class AuthorizationChangeEventService {
    static final String STREAM = "summer:authorization-change-events";
    private final AuthorizationChangeEventOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final AuthorizationChangeEventReceiptRepository receipts;
    private final AuthorizationDomainVersions versions;

    AuthorizationChangeEventService(AuthorizationChangeEventOutboxRepository repository, ObjectMapper objectMapper, AuthorizationChangeEventReceiptRepository receipts, AuthorizationDomainVersions versions) {
        this.repository = repository; this.objectMapper = objectMapper; this.receipts = receipts; this.versions = versions;
    }

    /** Legacy String calls are enterprise IDs; platform callers must provide an explicit Domain. */
    @Transactional
    public void enqueue(String enterpriseId, String resourceType, String resourceId, String eventType,
                        long version, String requestId) {
        if (enterpriseId == null || enterpriseId.isBlank()) throw new IllegalArgumentException("enterpriseId is required");
        versions.advance(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise(enterpriseId));
        persistEvent(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise(enterpriseId),
                enterpriseId, resourceType, resourceId, eventType, version, requestId);
    }

    /** Legacy enterprise events retain their payload key while selecting an unambiguous revision domain. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void enqueueEnterprise(String enterpriseId, String resourceType, String resourceId, String eventType,
                                  long version, String requestId) {
        versions.advance(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise(enterpriseId));
        persistEvent(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise(enterpriseId),
                enterpriseId, resourceType, resourceId, eventType, version, requestId);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void enqueue(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain domain, String resourceType, String resourceId, String eventType, long version, String requestId) {
        versions.advance(domain);
        persistEvent(domain, domain.key(), resourceType, resourceId, eventType, version, requestId);
    }
    private void persistEvent(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain domain,
                              String payloadKey, String resourceType, String resourceId, String eventType, long version, String requestId) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventId", eventId); payload.put("enterpriseId", payloadKey);
            payload.put("domainKey", domain.key());
            payload.put("resourceType", resourceType); payload.put("resourceId", resourceId);
            payload.put("eventType", eventType); payload.put("version", version);
            payload.put("requestId", requestId == null ? "" : requestId);
            repository.save(new AuthorizationChangeEventOutboxEntity(eventId, domain.key(), resourceType, resourceId,
                    eventType, version, requestId, objectMapper.writeValueAsString(payload), now));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Authorization change event cannot be serialized", exception);
        }
    }

    @Transactional(readOnly = true)
    public AuthorizationChangeEventPage list(String enterpriseId, String resourceType) {
        return list(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise(enterpriseId), resourceType);
    }

    @Transactional(readOnly = true)
    public AuthorizationChangeEventPage list(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain domain,
                                             String resourceType) {
        List<AuthorizationChangeEventOutboxEntity> rows = resourceType == null || resourceType.isBlank()
                ? repository.findAllByEnterpriseIdOrderByCreatedAtDesc(domain.key())
                : repository.findAllByEnterpriseIdAndResourceTypeOrderByCreatedAtDesc(domain.key(), resourceType);
        return new AuthorizationChangeEventPage(rows.stream().map(this::view).toList(), rows.size());
    }

    @Transactional
    public AuthorizationChangeEventView retry(String enterpriseId, String eventId) {
        return retry(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise(enterpriseId), eventId);
    }

    @Transactional
    public AuthorizationChangeEventView retry(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain domain,
                                              String eventId) {
        AuthorizationChangeEventOutboxEntity entity = repository.findById(eventId)
                .filter(row -> domain.key().equals(row.enterpriseId()))
                .orElseThrow(() -> SummerException.withMessage(CommonErrorCode.NOT_FOUND, "Authorization change event not found: " + eventId));
        receipts.findById(eventId).filter(receipt -> receipt.status() == AuthorizationChangeReceiptStatus.DEAD_LETTER)
                .ifPresent(receipts::delete);
        entity.resetForRetry(Instant.now());
        return view(repository.saveAndFlush(entity));
    }

    AuthorizationChangeEventView view(AuthorizationChangeEventOutboxEntity entity) {
        Map<String, Object> payload;
        try { payload = objectMapper.readValue(entity.payloadJson(), Map.class); }
        catch (RuntimeException ignored) { payload = Map.of(); }
        var owner = storedDomain(entity.enterpriseId());
        String compatibilityKey = payload.get("enterpriseId") instanceof String value ? value : entity.enterpriseId();
        return new AuthorizationChangeEventView(entity.eventId(), compatibilityKey, entity.resourceType(),
                entity.resourceId(), entity.eventType(), entity.resourceVersion(), entity.requestId(), entity.status(),
                entity.attempts(), entity.nextAttemptAt(), entity.lastError(), entity.createdAt(), entity.updatedAt(), payload,
                owner == null ? null : owner.key(), owner == null ? "LEGACY_UNRESOLVED" : "FULL_DOMAIN_KEY");
    }

    /** Parse an already-keyed persisted owner; never infer a domain kind from a historic bare ID. */
    static com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain storedDomain(String key) {
        if (key == null) return null;
        if (key.equals("PLATFORM:platform"))
            return com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform();
        if (key.startsWith("ENTERPRISE:") && key.substring(11).matches("[A-Za-z0-9_-]{1,80}"))
            return com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise(key.substring(11));
        return null;
    }
}

