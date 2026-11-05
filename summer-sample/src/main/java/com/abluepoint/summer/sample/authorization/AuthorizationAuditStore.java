package com.abluepoint.summer.sample.authorization;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import com.abluepoint.summer.security.authorization.ResourceAuthorization;

/** Persists fixed audit metadata; grants, tokens and arbitrary request details are never stored. */
@Service
public class AuthorizationAuditStore {
    private final AuthorizationAuditRepository repository;
    private final ThreadLocal<ResourceEvidence> resourceEvidence = new ThreadLocal<>();

    AuthorizationAuditStore(AuthorizationAuditRepository repository) { this.repository = repository; }

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
        ResourceEvidence evidence = resourceEvidence.get();
        boolean sameDecision = evidence != null
                && ("PLATFORM".equals(evidence.request.domain().kind())
                    || Objects.equals(enterpriseId, evidence.request.domain().id())
                    || Objects.equals(enterpriseId, evidence.request.domain().key()))
                && Objects.equals(principal, evidence.principal()) && Objects.equals(requestId, evidence.request.requestId())
                && Objects.equals(resourceId, "user:" + principal)
                && ("AUTHORIZATION_ALLOWED".equals(action) || "AUTHORIZATION_DENIED".equals(action));
        // The bound policy request supplies the trusted kind; a legacy platform role can belong to
        // an enterprise, so its bare audit argument cannot identify the decision's domain.
        var row = new AuthorizationAuditEntity(sameDecision ? evidence.request.domain().key() : enterpriseId,
                resourceId, action, principal, requestId, value(details, "permissionCode"),
                value(details, "decision"), value(details, "reasonCode"));
        if (sameDecision) row.evidence(evidence.request.resource().id(), evidence.revision);
        repository.saveAndFlush(row);
        if (sameDecision) evidence.recorded = true;
    }

    /** Bind evidence only to this synchronous policy evaluation, including its independent audit transaction. */
    Evaluation evaluateResource(ResourceAuthorization.Request request, long revision, boolean mergeLegacy,
                                Supplier<ResourceAuthorization.Decision> evaluate) {
        ResourceEvidence previous = resourceEvidence.get();
        ResourceEvidence current = mergeLegacy ? new ResourceEvidence(request, revision) : null;
        if (current == null) resourceEvidence.remove();
        else resourceEvidence.set(current);
        try { return new Evaluation(evaluate.get(), current != null && current.recorded); }
        finally {
            if (previous == null) resourceEvidence.remove();
            else resourceEvidence.set(previous);
        }
    }

    record Evaluation(ResourceAuthorization.Decision decision, boolean recorded) {}
    private static final class ResourceEvidence {
        final ResourceAuthorization.Request request;
        final long revision;
        boolean recorded;
        ResourceEvidence(ResourceAuthorization.Request request, long revision) {
            this.request = request; this.revision = revision;
        }
        String principal() { return request.principal() == null ? "" : request.principal().getName(); }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resourceDecision(com.abluepoint.summer.security.authorization.ResourceAuthorization.Request request,
                                 com.abluepoint.summer.security.authorization.ResourceAuthorization.Decision decision) {
        String principal = request.principal() == null ? "" : request.principal().getName();
        var row = new AuthorizationAuditEntity(request.domain().key(), "user:" + principal,
                decision.allowed() ? "RESOURCE_AUTHORIZATION_ALLOWED" : "RESOURCE_AUTHORIZATION_DENIED",
                principal, request.requestId(), "", decision.allowed() ? "ALLOW" : "DENY", decision.reason());
        row.evidence(request.resource().id(), decision.revision());
        repository.saveAndFlush(row);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> read(ResourceAuthorization.Domain domain, String resourceId) {
        return read(domain.key(), resourceId);
    }

    /** Exact stored key only. Bare historic rows remain unresolved, never unioned with a full key. */
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
