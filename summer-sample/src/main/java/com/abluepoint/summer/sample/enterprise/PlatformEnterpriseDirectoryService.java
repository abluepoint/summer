package com.abluepoint.summer.sample.enterprise;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;

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
class PlatformEnterpriseDirectoryService {
    private final com.abluepoint.summer.sample.resource.ManagedConsumerAuthorization managedAccess;

    private final EnterpriseRepository repository;
    private final EnterpriseRegistry registry;
    private final DirectoryJournal journal;
    PlatformEnterpriseDirectoryService(EnterpriseRepository repository, EnterpriseRegistry registry, DirectoryJournal journal) { this(repository, registry, journal, null); }

    @org.springframework.beans.factory.annotation.Autowired
    PlatformEnterpriseDirectoryService(EnterpriseRepository repository, EnterpriseRegistry registry, DirectoryJournal journal, com.abluepoint.summer.sample.resource.ManagedConsumerAuthorization managedAccess) {
        this.managedAccess = managedAccess;
        this.repository = repository; this.registry = registry; this.journal = journal;
    }

    @Transactional(readOnly = true)
    PlatformEnterprisePage list(String search, String status, String qualification, Authentication auth) {
        requireAccess(auth, "platform.enterprise.read");
        String query = search == null ? "" : search.trim().toLowerCase(java.util.Locale.ROOT);
        List<PlatformEnterpriseView> all = repository.findAll().stream().map(PlatformEnterpriseDirectoryService::view)
                .sorted(Comparator.comparing(PlatformEnterpriseView::name).thenComparing(PlatformEnterpriseView::enterpriseId)).toList();
        List<PlatformEnterpriseView> filtered = all.stream()
                .filter(value -> query.isBlank() || value.name().toLowerCase(java.util.Locale.ROOT).contains(query) || value.enterpriseId().contains(query))
                .filter(value -> status == null || status.isBlank() || "全部".equals(status) || status.equals(value.status()))
                .filter(value -> qualification == null || qualification.isBlank() || "全部".equals(qualification) || qualification.equals(value.license()))
                .toList();
        var summary = new PlatformEnterpriseSummary(all.size(), all.stream().filter(v -> "已认证".equals(v.status())).count(),
                all.stream().filter(v -> "待审核".equals(v.status())).count(),
                all.stream().filter(v -> List.of("已冻结", "已停用", "已驳回").contains(v.status())).count());
        return new PlatformEnterprisePage(filtered, 1, filtered.size(), filtered.size(), summary);
    }

    @Transactional
    PlatformEnterpriseView transition(String id, String action, String ifMatch, String key, String requestId, Authentication auth) {
        requireAccess(auth, "platform.enterprise.write");
        if (key == null || key.isBlank() || key.length() > 128) throw SummerException.withMessage(DirectoryErrorCode.INVALID_ENTERPRISE_ACTION, "Idempotency-Key is required (maximum 128 characters)");
        if (!repository.existsByEnterpriseId(id)) throw SummerException.withMessage(DirectoryErrorCode.ENTERPRISE_NOT_FOUND, "Enterprise not found: " + id);
        registry.lock(id);
        String fingerprint = action + "|" + ifMatch;
        var previous = journal.find(id, "platform.enterprise.mutation", key).orElse(null);
        if (previous != null) {
            if (!previous.fingerprint().equals(fingerprint)) throw SummerException.withMessage(DirectoryErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency-Key was already used for another enterprise action");
            return journal.decode(previous, PlatformEnterpriseView.class);
        }
        var current = repository.findByEnterpriseId(id).orElseThrow(() -> SummerException.withMessage(DirectoryErrorCode.ENTERPRISE_NOT_FOUND, "Enterprise not found: " + id));
        if (!("\"" + revision(current) + "\"").equals(ifMatch)) throw SummerException.withMessage(SampleTransportErrorCode.PRECONDITION_FAILED, "The enterprise has changed");
        String next = switch (action) {
            case "freeze" -> "FROZEN";
            case "disable" -> "DISABLED";
            case "reject" -> "REJECTED";
            default -> throw SummerException.withMessage(DirectoryErrorCode.INVALID_ENTERPRISE_ACTION, "unsupported enterprise action");
        };
        String before = current.status();
        current.status(next);
        repository.saveAndFlush(current);
        var result = view(current);
        journal.remember(id, "platform.enterprise.mutation", key, fingerprint, result);
        journal.audit(id, "platform.enterprise.audit", id, new EnterpriseAudit(auth.getName(), action, before, next, requestId));
        return result;
    }

    static void require(Authentication auth, String capability) {
        if (!(auth instanceof JwtAuthenticationToken jwt)) throw new AccessDeniedException("platform capability required");
        Map<String, Object> claims = jwt.getToken().getClaims();
        Object memberships = claims.get("enterprise_memberships");
        if (memberships instanceof Collection<?> values && !values.isEmpty() && !"USER".equals(String.valueOf(claims.get("principal_type"))))
            throw new AccessDeniedException("platform identity required");
        Object claim = claims.get("platform_capabilities");
        if (!(claim instanceof Collection<?> values) || values.stream().noneMatch(value -> capability.equals(String.valueOf(value))))
            throw new AccessDeniedException("platform capability required");
    }
    static String revision(EnterpriseEntity value) { return value.enterpriseId() + ":v" + value.version(); }
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
    record EnterpriseAudit(String actorSubject, String action, String beforeStatus, String afterStatus, String requestId) {}
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
