package com.abluepoint.summer.sample.enterprise;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class EnterpriseReviewService {
    private final com.abluepoint.summer.sample.resource.ManagedConsumerAuthorization managedAccess;

    private final EnterpriseRepository repository;
    private final EnterpriseRegistry registry;
    private final DirectoryJournal journal;
    EnterpriseReviewService(EnterpriseRepository repository, EnterpriseRegistry registry, DirectoryJournal journal) { this(repository, registry, journal, null); }

    @org.springframework.beans.factory.annotation.Autowired
    EnterpriseReviewService(EnterpriseRepository repository, EnterpriseRegistry registry, DirectoryJournal journal, com.abluepoint.summer.sample.resource.ManagedConsumerAuthorization managedAccess) {
        this.managedAccess = managedAccess;
        this.repository = repository; this.registry = registry; this.journal = journal;
    }
    @Transactional(readOnly = true)
    List<EnterpriseReview> list(Authentication auth) {
        requireAccess(auth, "platform.enterprise-review.read");
        return repository.findAll().stream().map(EnterpriseReviewService::view).sorted(java.util.Comparator.comparing(EnterpriseReview::applicationId)).toList();
    }
    @Transactional(readOnly = true)
    EnterpriseReview detail(String id, Authentication auth) {
        requireAccess(auth, "platform.enterprise-review.read");
        return view(find(id));
    }
    @Transactional
    EnterpriseReview decide(String id, String decision, String reason, String ifMatch, String key, String requestId, Authentication auth) {
        requireAccess(auth, "platform.enterprise-review.write");
        if (key == null || key.isBlank() || key.length() > 128) throw SummerException.withMessage(DirectoryErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency-Key is required (maximum 128 characters)");
        String tenant = find(id).enterpriseId();
        registry.lock(tenant);
        String fingerprint = decision + "|" + reason + "|" + ifMatch;
        var previous = journal.find(tenant, "enterprise.review.mutation", key).orElse(null);
        if (previous != null) {
            if (!previous.fingerprint().equals(fingerprint)) throw SummerException.withMessage(DirectoryErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency-Key was already used for another decision");
            return journal.decode(previous, EnterpriseReview.class);
        }
        var current = find(id);
        if (!("\"" + PlatformEnterpriseDirectoryService.revision(current) + "\"").equals(ifMatch)) throw SummerException.withMessage(SampleTransportErrorCode.PRECONDITION_FAILED, "The resource has changed");
        if (("REJECT".equals(decision) || "REQUEST_INFO".equals(decision)) && (reason == null || reason.isBlank())) throw SummerException.withMessage(DirectoryErrorCode.INVALID_REVIEW, "reason is required");
        String after = switch (decision) { case "APPROVE" -> "APPROVED"; case "REJECT" -> "REJECTED"; case "REQUEST_INFO" -> "NEEDS_INFO"; default -> throw SummerException.withMessage(DirectoryErrorCode.INVALID_REVIEW, "unsupported decision"); };
        String before = current.reviewStatus();
        if (!"PENDING".equals(before) && !"NEEDS_INFO".equals(before)) throw SummerException.withMessage(DirectoryErrorCode.INVALID_REVIEW, "illegal status transition");
        current.reviewStatus(after);
        repository.saveAndFlush(current);
        var result = view(current);
        journal.audit(tenant, "enterprise.review.audit", id, new ReviewDecision(auth.getName(), id, decision, reason, before, after, requestId));
        journal.remember(tenant, "enterprise.review.mutation", key, fingerprint, result);
        return result;
    }
    @Transactional(readOnly = true)
    List<ReviewDecision> audit(String id, Authentication auth) {
        requireAccess(auth, "platform.enterprise-review.read");
        find(id);
        return journal.audit(tenant(id), "enterprise.review.audit", id, ReviewDecision.class);
    }
    private EnterpriseEntity find(String id) {
        return repository.findByEnterpriseId(tenant(id)).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
    }
    private static String tenant(String id) {
        if (id == null || !id.startsWith("application-")) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return "ent-" + id.substring("application-".length());
    }
    private static EnterpriseReview view(EnterpriseEntity value) {
        return new EnterpriseReview("application-" + value.enterpriseId().substring("ent-".length()), value.enterpriseId(), value.name(), value.reviewStatus(), List.of(), PlatformEnterpriseDirectoryService.revision(value));
    }
    private void requireAccess(Authentication actor, String scope) {
        if (managedAccess != null && managedAccess.require(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), actor, scope)) {
            if (actor instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt) {
                Object memberships = jwt.getToken().getClaims().get("enterprise_memberships");
                if (memberships instanceof java.util.Collection<?> values && !values.isEmpty()
                    && !"USER".equals(String.valueOf(jwt.getToken().getClaims().get("principal_type"))))
                    throw new org.springframework.security.access.AccessDeniedException("platform identity required");
            }
            return;
        }
        PlatformEnterpriseDirectoryService.require(actor, scope);
    }

}
