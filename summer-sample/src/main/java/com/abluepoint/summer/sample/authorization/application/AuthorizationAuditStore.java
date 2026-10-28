package com.abluepoint.summer.sample.authorization.application;

import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationAuditEntity;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationAuditRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

/** Persists fixed audit metadata; grants, tokens and arbitrary request details are never stored. */
@Service
@org.springframework.modulith.NamedInterface("audit")
public class AuthorizationAuditStore {
    private final AuthorizationAuditRepository repository;

    public AuthorizationAuditStore(AuthorizationAuditRepository repository) { this.repository = repository; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void mutation(String enterpriseId, String resourceId, String action, String requestId, String principal) {
        // Flush before the caller enqueues its outbox event; both still share the caller's transaction.
        repository.saveAndFlush(new AuthorizationAuditEntity(enterpriseId, resourceId, action, principal,
                requestId, "", "", ""));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decision(String enterpriseId, String resourceId, String action, String requestId, String principal,
                         Map<String, Object> details) {
        // Decisions are facts independent of the business outcome. Do not read or lock mutation entities here.
        repository.saveAndFlush(new AuthorizationAuditEntity(enterpriseId, resourceId, action, principal, requestId,
                value(details, "permissionCode"), value(details, "decision"), value(details, "reasonCode")));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> read(String enterpriseId, String resourceId) {
        return repository.findByEnterpriseIdAndResourceIdInOrderByAuditIdAsc(enterpriseId, List.of(resourceId, "user:" + resourceId))
                .stream().map(AuthorizationAuditEntity::view).toList();
    }

    private static String value(Map<String, Object> details, String key) {
        Object value = details.get(key);
        return value instanceof String text ? text : "";
    }
}
