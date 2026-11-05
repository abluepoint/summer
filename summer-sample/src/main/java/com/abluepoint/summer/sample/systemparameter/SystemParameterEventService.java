package com.abluepoint.summer.sample.systemparameter;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
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
    private final com.abluepoint.summer.sample.authorization.BusinessDataScopes scopes;
    private final com.abluepoint.summer.sample.resource.BusinessResourceAccess business;
    private final SystemParameterRepository parameters;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entities;

    SystemParameterEventService(SystemParameterEventOutboxRepository repository, ObjectMapper objectMapper,
        com.abluepoint.summer.sample.authorization.BusinessDataScopes scopes,
        com.abluepoint.summer.sample.resource.BusinessResourceAccess business,SystemParameterRepository parameters) {
        this.scopes=scopes;this.business=business;this.parameters=parameters;
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
    SystemParameterEventPage list(String tenant, String key, org.springframework.security.core.Authentication actor) {
        business.require(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise(tenant),actor,
            "system.parameter.read","platform.parameter.read","customer.read");
        return scopes.read(tenant,"system-parameters","read",actor,range -> {
            var builder=entities.getCriteriaBuilder();var query=builder.createQuery(SystemParameterEventOutboxEntity.class);
            var event=query.from(SystemParameterEventOutboxEntity.class);var parent=query.subquery(Integer.class);
            var parameter=parent.from(SystemParameterEntity.class);
            parent.select(builder.literal(1)).where(builder.and(
                builder.equal(parameter.get("tenantId"),event.get("tenantId")),
                builder.equal(parameter.get("key"),event.get("parameterKey")),
                range.<SystemParameterEntity>specification().toPredicate(parameter,null,builder)));
            var predicates=new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(builder.equal(event.get("tenantId"),tenant));predicates.add(builder.exists(parent));
            if(key!=null && !key.isBlank()) predicates.add(builder.equal(event.get("parameterKey"),key));
            query.select(event).where(builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new)))
                .orderBy(builder.desc(event.get("createdAt")));
            var rows=entities.createQuery(query).getResultList();
            return new SystemParameterEventPage(rows.stream().map(this::view).toList(),rows.size());
        });
    }

    @Transactional
    SystemParameterEventView retry(String tenant, String eventId, org.springframework.security.core.Authentication actor) {
        business.require(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise(tenant),actor,
            "system.parameter.update","platform.parameter.update","system.parameter.write","platform.parameter.write","customer.write");
        var requested=repository.findById(eventId).orElseThrow(() -> missing(eventId));
        if(!tenant.equals(requested.tenantId())) throw missing(eventId);
        return scopes.write(tenant,"system-parameters",java.util.Set.of("update"),actor,ranges -> {
            var range=ranges.get("update");
            var entity=repository.findById(eventId).orElseThrow(() -> missing(eventId));
            if(!tenant.equals(entity.tenantId()) || parameters.findScoped(entity.parameterKey(),range).isEmpty()) throw missing(eventId);
            entities.detach(entity);entity.resetForRetry(Instant.now());
            scopes.updateParameterEvent(range,eventId,tenant,entity.parameterKey(),entity.status().name(),entity.nextAttemptAt(),entity.updatedAt());
            return view(repository.findById(eventId).orElseThrow(() -> missing(eventId)));
        });
    }
    private static SummerException missing(String eventId) {
        return SummerException.withMessage(CommonErrorCode.NOT_FOUND,"System parameter event not found: "+eventId);
    }

    SystemParameterEventView view(SystemParameterEventOutboxEntity entity) {
        return new SystemParameterEventView(entity.eventId(), entity.tenantId(), entity.parameterKey(),
                entity.eventType(), entity.parameterVersion(), entity.requestId(), entity.status(),
                entity.attempts(), entity.nextAttemptAt(), entity.lastError(), entity.createdAt(),
                entity.updatedAt(), new SystemParameterEventBody(entity.eventId(), entity.tenantId(),
                entity.parameterKey(), entity.eventType(), entity.parameterVersion(), entity.requestId()));
    }
}

