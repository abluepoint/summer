package com.abluepoint.summer.sample.enterprise;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class EnterpriseReviewService {
    private final EnterpriseRepository repository;
    private final EnterpriseRegistry registry;
    private final DirectoryJournal journal;
    EnterpriseReviewService(EnterpriseRepository repository, EnterpriseRegistry registry, DirectoryJournal journal) {
        this.repository = repository; this.registry = registry; this.journal = journal;
    }
    @Transactional(readOnly = true)
    List<EnterpriseReview> list(Authentication auth) {
        PlatformEnterpriseDirectoryService.require(auth, "platform.enterprise-review.read");
        return repository.findAll().stream().map(EnterpriseReviewService::view).sorted(java.util.Comparator.comparing(EnterpriseReview::applicationId)).toList();
    }
    @Transactional(readOnly = true)
    EnterpriseReview detail(String id, Authentication auth) {
        PlatformEnterpriseDirectoryService.require(auth, "platform.enterprise-review.read");
        return view(find(id));
    }
    @Transactional
    EnterpriseReview decide(String id, String decision, String reason, String ifMatch, String key, String requestId, Authentication auth) {
        PlatformEnterpriseDirectoryService.require(auth, "platform.enterprise-review.write");
        if (key == null || key.isBlank() || key.length() > 128) throw new ReviewConflictException("Idempotency-Key is required (maximum 128 characters)");
        String tenant = find(id).enterpriseId();
        registry.lock(tenant);
        String fingerprint = decision + "|" + reason + "|" + ifMatch;
        var previous = journal.find(tenant, "enterprise.review.mutation", key).orElse(null);
        if (previous != null) {
            if (!previous.fingerprint().equals(fingerprint)) throw new ReviewConflictException("Idempotency-Key was already used for another decision");
            return journal.decode(previous, EnterpriseReview.class);
        }
        var current = find(id);
        if (!("\"" + PlatformEnterpriseDirectoryService.revision(current) + "\"").equals(ifMatch)) throw new ReviewPreconditionException();
        if (("REJECT".equals(decision) || "REQUEST_INFO".equals(decision)) && (reason == null || reason.isBlank())) throw new ReviewValidationException("reason is required");
        String after = switch (decision) { case "APPROVE" -> "APPROVED"; case "REJECT" -> "REJECTED"; case "REQUEST_INFO" -> "NEEDS_INFO"; default -> throw new ReviewValidationException("unsupported decision"); };
        String before = current.reviewStatus();
        if (!"PENDING".equals(before) && !"NEEDS_INFO".equals(before)) throw new ReviewValidationException("illegal status transition");
        current.reviewStatus(after);
        repository.saveAndFlush(current);
        var result = view(current);
        journal.audit(tenant, "enterprise.review.audit", id, new ReviewDecision(auth.getName(), id, decision, reason, before, after, requestId));
        journal.remember(tenant, "enterprise.review.mutation", key, fingerprint, result);
        return result;
    }
    @Transactional(readOnly = true)
    List<ReviewDecision> audit(String id, Authentication auth) {
        PlatformEnterpriseDirectoryService.require(auth, "platform.enterprise-review.read");
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
}
