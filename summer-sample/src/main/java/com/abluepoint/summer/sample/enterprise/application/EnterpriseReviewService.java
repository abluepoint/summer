package com.abluepoint.summer.sample.enterprise.application;

import com.abluepoint.summer.sample.enterprise.infrastructure.DirectoryJournal;
import com.abluepoint.summer.sample.enterprise.infrastructure.EnterpriseEntity;
import com.abluepoint.summer.sample.enterprise.infrastructure.EnterpriseRepository;
import com.abluepoint.summer.sample.enterprise.domain.ReviewPreconditionException;
import com.abluepoint.summer.sample.enterprise.domain.ReviewConflictException;
import com.abluepoint.summer.sample.enterprise.domain.ReviewValidationException;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnterpriseReviewService {
    private final EnterpriseRepository repository;
    private final EnterpriseRegistry registry;
    private final DirectoryJournal journal;
    EnterpriseReviewService(EnterpriseRepository repository, EnterpriseRegistry registry, DirectoryJournal journal) {
        this.repository = repository; this.registry = registry; this.journal = journal;
    }
    @Transactional(readOnly = true)
    public List<EnterpriseReviewView> list(Authentication auth) {
        com.abluepoint.summer.sample.enterprise.infrastructure.PlatformEnterpriseAccess.require(auth, "platform.enterprise-review.read");
        return repository.findAll().stream().map(EnterpriseReviewService::view).sorted(java.util.Comparator.comparing(EnterpriseReviewView::applicationId)).toList();
    }
    @Transactional(readOnly = true)
    public EnterpriseReviewView detail(String id, Authentication auth) {
        com.abluepoint.summer.sample.enterprise.infrastructure.PlatformEnterpriseAccess.require(auth, "platform.enterprise-review.read");
        return view(find(id));
    }
    @Transactional
    public EnterpriseReviewView decide(String id, String decision, String reason, String ifMatch, String key, String requestId, Authentication auth) {
        com.abluepoint.summer.sample.enterprise.infrastructure.PlatformEnterpriseAccess.require(auth, "platform.enterprise-review.write");
        if (key == null || key.isBlank() || key.length() > 128) throw new ReviewConflictException("Idempotency-Key is required (maximum 128 characters)");
        String tenant = find(id).enterpriseId();
        registry.lock(tenant);
        String fingerprint = decision + "|" + reason + "|" + ifMatch;
        var previous = journal.find(tenant, "enterprise.review.mutation", key).orElse(null);
        if (previous != null) {
            if (!previous.fingerprint().equals(fingerprint)) throw new ReviewConflictException("Idempotency-Key was already used for another decision");
            return journal.decode(previous, EnterpriseReviewView.class);
        }
        var current = find(id);
        if (!com.abluepoint.summer.sample.enterprise.domain.EnterpriseRules.matchesVersion(PlatformEnterpriseDirectoryService.revision(current), ifMatch)) throw new ReviewPreconditionException();
        String before = current.reviewStatus();
        String after = com.abluepoint.summer.sample.enterprise.domain.EnterpriseRules.review(before, decision, reason);
        current.reviewStatus(after);
        repository.saveAndFlush(current);
        var result = view(current);
        journal.audit(tenant, "enterprise.review.audit", id, new EnterpriseReviewDecisionView(auth.getName(), id, decision, reason, before, after, requestId));
        journal.remember(tenant, "enterprise.review.mutation", key, fingerprint, result);
        return result;
    }
    @Transactional(readOnly = true)
    public List<EnterpriseReviewDecisionView> audit(String id, Authentication auth) {
        com.abluepoint.summer.sample.enterprise.infrastructure.PlatformEnterpriseAccess.require(auth, "platform.enterprise-review.read");
        find(id);
        return journal.audit(tenant(id), "enterprise.review.audit", id, EnterpriseReviewDecisionView.class);
    }
    private EnterpriseEntity find(String id) {
        return repository.findByEnterpriseId(tenant(id)).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
    }
    private static String tenant(String id) {
        if (id == null || !id.startsWith("application-")) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return "ent-" + id.substring("application-".length());
    }
    private static EnterpriseReviewView view(EnterpriseEntity value) {
        return new EnterpriseReviewView("application-" + value.enterpriseId().substring("ent-".length()), value.enterpriseId(), value.name(), value.reviewStatus(), List.of(), PlatformEnterpriseDirectoryService.revision(value));
    }
}
