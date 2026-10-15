package com.abluepoint.summer.sample.customer;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CustomerAuditService {
    static final String TENANT_ID = "ent-acme";
    private final CustomerAuditEventRepository repository;
    private final ObjectMapper objectMapper;
    private final AuditOrganizationContext organizationContext;

    CustomerAuditService(CustomerAuditEventRepository repository, ObjectMapper objectMapper,
                         AuditOrganizationContext organizationContext) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.organizationContext = organizationContext;
    }

    @Transactional
    public void recordSuccess(String username, String action, String requestId, Authentication authentication) {
        recordResource("customer:" + username, action, "SUCCESS", requestId, authentication, Map.of());
    }

    @Transactional
    public void recordResourceSuccess(String resource, String action, String requestId,
                                      Authentication authentication) {
        recordResource(resource, action, "SUCCESS", requestId, authentication, Map.of());
    }

    @Transactional
    public void recordResourceFailure(String resource, String action, String requestId,
                                      Authentication authentication, String reason) {
        recordResource(resource, action, "FAILURE", requestId, authentication,
                Map.of("reason", reason == null ? "operation_failed" : reason));
    }

    @Transactional
    public void recordTenantResourceSuccess(String tenant, String resource, String action, String requestId,
                                            Authentication authentication) {
        String principal = authentication == null ? "system" : authentication.getName();
        repository.save(new CustomerAuditEventEntity(UUID.randomUUID().toString(), tenant,
                organizationContext.current(tenant, authentication), principal,
                resource, action, "SUCCESS", requestId, "{}", Instant.now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String username, String action, String requestId, Authentication authentication, String reason) {
        // Reasons are intentionally bounded and contain no request body or database details.
        recordResource("customer:" + username, action, "FAILURE", requestId, authentication,
                Map.of("reason", reason == null ? "operation_failed" : reason));
    }

    @Transactional
    public void recordDictionarySuccess(String code, String action, String requestId, Authentication authentication) {
        recordResource("dictionary:" + code, action, "SUCCESS", requestId, authentication, Map.of());
    }

    @Transactional(readOnly = true)
    CustomerAuditPage search(CustomerAuditQuery query, Pageable pageable) {
        Page<CustomerAuditEventEntity> page = repository.findAll(specification(query), pageable);
        return new CustomerAuditPage(page.getContent().stream().map(this::view).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    String export(CustomerAuditQuery query) {
        Page<CustomerAuditEventEntity> page = repository.findAll(specification(query),
                org.springframework.data.domain.PageRequest.of(0, 1000,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "occurredAt")));
        StringBuilder csv = new StringBuilder("eventId,tenantId,organizationId,principal,resource,action,result,requestId,occurredAt\n");
        for (CustomerAuditEventEntity event : page.getContent()) {
            csv.append(String.join(",", csvValue(event.eventId()), csvValue(event.tenantId()),
                    csvValue(event.organizationId()), csvValue(event.principal()), csvValue(event.resource()),
                    csvValue(event.action()), csvValue(event.result()), csvValue(event.requestId()),
                    csvValue(event.occurredAt()))).append('\n');
        }
        return csv.toString();
    }

    private Specification<CustomerAuditEventEntity> specification(CustomerAuditQuery query) {
        return (root, ignored, criteria) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteria.equal(root.get("tenantId"), TENANT_ID));
            if (present(query.principal())) predicates.add(criteria.equal(root.get("principal"), query.principal()));
            if (present(query.resource())) predicates.add(criteria.equal(root.get("resource"), query.resource()));
            if (present(query.action())) predicates.add(criteria.equal(root.get("action"), query.action()));
            if (present(query.result())) predicates.add(criteria.equal(root.get("result"), query.result()));
            if (present(query.requestId())) predicates.add(criteria.equal(root.get("requestId"), query.requestId()));
            if (query.from() != null) predicates.add(criteria.greaterThanOrEqualTo(root.get("occurredAt"), query.from()));
            if (query.to() != null) predicates.add(criteria.lessThanOrEqualTo(root.get("occurredAt"), query.to()));
            return criteria.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    CustomerAuditEventView find(String eventId) {
        return repository.findById(eventId).filter(event -> TENANT_ID.equals(event.tenantId()))
                .map(this::view).orElseThrow(() -> new CustomerAuditNotFoundException("Audit event not found"));
    }

    private void recordResource(String resource, String action, String result, String requestId,
                                Authentication authentication, Map<String, Object> detail) {
        String principal = authentication == null || authentication.getName() == null
                ? "system" : authentication.getName();
        repository.save(new CustomerAuditEventEntity(
                UUID.randomUUID().toString(), TENANT_ID, organizationContext.current(TENANT_ID, authentication),
                principal, resource, action, result,
                requestId, serialize(detail), Instant.now()));
    }

    private CustomerAuditEventView view(CustomerAuditEventEntity event) {
        return new CustomerAuditEventView(event.eventId(), event.tenantId(), event.organizationId(),
                event.principal(), event.resource(), event.action(), event.result(), event.requestId(),
                parse(event.detailJson()), event.occurredAt());
    }

    private String serialize(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail == null ? Map.of() : detail);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Audit detail cannot be serialized", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String detail) {
        if (!present(detail)) return Map.of();
        try {
            Map<String, Object> parsed = objectMapper.readValue(detail, Map.class);
            return parsed == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(parsed));
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private static boolean present(String value) { return value != null && !value.isBlank(); }

    private static String csvValue(Object value) {
        if (value == null) return "";
        return '"' + String.valueOf(value).replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + '"';
    }
}

record CustomerAuditQuery(String principal, String resource, String action, String result,
                          String requestId, Instant from, Instant to) {}

final class CustomerAuditNotFoundException extends RuntimeException {
    CustomerAuditNotFoundException(String message) { super(message); }
}
