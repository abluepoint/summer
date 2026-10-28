package com.abluepoint.summer.sample.enterprise.application;

import com.abluepoint.summer.sample.enterprise.infrastructure.DirectoryJournal;
import com.abluepoint.summer.sample.enterprise.infrastructure.EnterpriseEntity;
import com.abluepoint.summer.sample.enterprise.infrastructure.EnterpriseRepository;
import com.abluepoint.summer.sample.enterprise.domain.PlatformEnterpriseNotFoundException;
import com.abluepoint.summer.sample.enterprise.domain.PlatformEnterprisePreconditionException;
import com.abluepoint.summer.sample.enterprise.domain.PlatformEnterpriseConflictException;
import com.abluepoint.summer.sample.enterprise.domain.PlatformEnterpriseValidationException;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformEnterpriseDirectoryService {
    private final EnterpriseRepository repository;
    private final EnterpriseRegistry registry;
    private final DirectoryJournal journal;
    PlatformEnterpriseDirectoryService(EnterpriseRepository repository, EnterpriseRegistry registry, DirectoryJournal journal) {
        this.repository = repository; this.registry = registry; this.journal = journal;
    }

    @Transactional(readOnly = true)
    public PlatformEnterprisePageView list(String search, String status, String qualification, Authentication auth) {
        com.abluepoint.summer.sample.enterprise.infrastructure.PlatformEnterpriseAccess.require(auth, "platform.enterprise.read");
        String query = search == null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT);
        List<PlatformEnterpriseView> all = repository.findAll().stream().map(PlatformEnterpriseDirectoryService::view)
                .sorted(Comparator.comparing(PlatformEnterpriseView::name).thenComparing(PlatformEnterpriseView::enterpriseId)).toList();
        List<PlatformEnterpriseView> filtered = all.stream()
                .filter(value -> query.isBlank() || value.name().toLowerCase(java.util.Locale.ROOT).contains(query) || value.enterpriseId().contains(query))
                .filter(value -> status == null || status.isBlank() || "全部".equals(status) || status.equals(value.status()))
                .filter(value -> qualification == null || qualification.isBlank() || "全部".equals(qualification) || qualification.equals(value.license()))
                .toList();
        var summary = new PlatformEnterpriseSummaryView(all.size(), all.stream().filter(v -> "已认证".equals(v.status())).count(),
                all.stream().filter(v -> "待审核".equals(v.status())).count(),
                all.stream().filter(v -> List.of("已冻结", "已停用", "已驳回").contains(v.status())).count());
        return new PlatformEnterprisePageView(filtered, 1, filtered.size(), filtered.size(), summary);
    }

    @Transactional
    public PlatformEnterpriseView transition(String id, String action, String ifMatch, String key, String requestId, Authentication auth) {
        com.abluepoint.summer.sample.enterprise.infrastructure.PlatformEnterpriseAccess.require(auth, "platform.enterprise.write");
        if (key == null || key.isBlank() || key.length() > 128) throw new PlatformEnterpriseValidationException("Idempotency-Key is required (maximum 128 characters)");
        if (!repository.existsByEnterpriseId(id)) throw new PlatformEnterpriseNotFoundException(id);
        registry.lock(id);
        String fingerprint = action + "|" + ifMatch;
        var previous = journal.find(id, "platform.enterprise.mutation", key).orElse(null);
        if (previous != null) {
            if (!previous.fingerprint().equals(fingerprint)) throw new PlatformEnterpriseConflictException("Idempotency-Key was already used for another enterprise action");
            return journal.decode(previous, PlatformEnterpriseView.class);
        }
        var current = repository.findByEnterpriseId(id).orElseThrow(() -> new PlatformEnterpriseNotFoundException(id));
        if (!com.abluepoint.summer.sample.enterprise.domain.EnterpriseRules.matchesVersion(revision(current), ifMatch)) throw new PlatformEnterprisePreconditionException();
        String next = com.abluepoint.summer.sample.enterprise.domain.EnterpriseRules.transition(action);
        String before = current.status();
        current.status(next);
        repository.saveAndFlush(current);
        var result = view(current);
        journal.remember(id, "platform.enterprise.mutation", key, fingerprint, result);
        journal.audit(id, "platform.enterprise.audit", id, new EnterpriseAuditView(auth.getName(), action, before, next, requestId));
        return result;
    }

    public static String revision(EnterpriseEntity value) { return com.abluepoint.summer.sample.enterprise.domain.EnterpriseRules.revision(value.enterpriseId(), value.version()); }
    private static PlatformEnterpriseView view(EnterpriseEntity value) {
        String status = switch (value.status()) {
            case "FROZEN" -> "已冻结";
            case "DISABLED" -> "已停用";
            case "REJECTED" -> "已驳回";
            default -> switch (value.reviewStatus()) {
                case "APPROVED" -> "已认证";
                case "REJECTED" -> "已驳回";
                case "NEEDS_INFO" -> "待补充资料";
                default -> "待审核";
            };
        };
        // No credential, DID or resource-count evidence exists in this directory yet.
        return new PlatformEnterpriseView(value.enterpriseId(), value.name(), "", "", status, "", "", "", revision(value));
    }


}
