package com.abluepoint.summer.sample.enterprise;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
final class PlatformEnterpriseDirectoryService {
    private final Map<String, EnterpriseState> enterprises = new ConcurrentHashMap<>();
    private final Map<String, Receipt> idempotency = new ConcurrentHashMap<>();

    PlatformEnterpriseDirectoryService() {
        add("ent-bjai", "北京AI很厉害科技有限公司", "did:aict:enterprise:bjai****", "有效", "已认证", "营业执照", "328 / 12 / 46", "2026-08-19 09:42");
        add("ent-hdim", "华东智能制造集团", "did:aict:enterprise:hdim****", "有效", "待审核", "资质文件", "1,246 / 31 / 118", "2026-08-19 08:16");
        add("ent-safe", "数安联合实验室", "did:aict:enterprise:safe****", "将过期", "异常", "证照", "82 / 5 / 19", "证照即将过期");
        add("ent-nfer", "南方能源研究院", "did:aict:enterprise:nfer****", "有效", "已认证", "营业执照", "536 / 18 / 77", "2026-08-18 19:22");
    }

    PlatformEnterprisePage list(String search, String status, String qualification, Authentication auth) {
        require(auth, "platform.enterprise.read");
        String normalizedSearch = search == null ? "" : search.trim();
        String normalizedStatus = status == null ? "" : status.trim();
        String normalizedQualification = qualification == null ? "" : qualification.trim();
        List<EnterpriseState> all = enterprises.values().stream().sorted(Comparator.comparing(EnterpriseState::name)).toList();
        List<EnterpriseState> filtered = all.stream()
                .filter(value -> normalizedSearch.isBlank() || value.name().contains(normalizedSearch) || value.did().contains(normalizedSearch))
                .filter(value -> normalizedStatus.isBlank() || "全部".equals(normalizedStatus) || value.status().equals(normalizedStatus))
                .filter(value -> normalizedQualification.isBlank() || "全部".equals(normalizedQualification) || value.license().equals(normalizedQualification))
                .toList();
        PlatformEnterpriseSummary summary = new PlatformEnterpriseSummary(
                all.size(),
                all.stream().filter(value -> "已认证".equals(value.status())).count(),
                all.stream().filter(value -> "待审核".equals(value.status())).count(),
                all.stream().filter(value -> "异常".equals(value.status())).count());
        List<PlatformEnterpriseView> items = filtered.stream().map(EnterpriseState::view).toList();
        return new PlatformEnterprisePage(items, 1, items.size(), items.size(), summary);
    }

    synchronized PlatformEnterpriseView transition(String id, String action, String ifMatch, String key, String requestId, Authentication auth) {
        require(auth, "platform.enterprise.write");
        if (key == null || key.isBlank()) throw new PlatformEnterpriseValidationException("Idempotency-Key is required");
        EnterpriseState current = enterprises.get(id);
        if (current == null) throw new PlatformEnterpriseNotFoundException(id);
        String fingerprint = action + "|" + ifMatch;
        String operation = id + ":" + key;
        Receipt previous = idempotency.get(operation);
        if (previous != null) {
            if (!previous.fingerprint().equals(fingerprint)) throw new PlatformEnterpriseConflictException("Idempotency-Key was already used for another enterprise action");
            return previous.view();
        }
        if (!quote(current.revision()).equals(ifMatch)) throw new PlatformEnterprisePreconditionException();
        String nextStatus = switch (action) {
            case "freeze" -> "已冻结";
            case "disable" -> "已停用";
            case "reject" -> "已驳回";
            default -> throw new PlatformEnterpriseValidationException("unsupported enterprise action");
        };
        EnterpriseState updated = current.withStatus(nextStatus, action.equals("reject") ? "审核驳回" : current.activity());
        enterprises.put(id, updated);
        PlatformEnterpriseView view = updated.view();
        idempotency.put(operation, new Receipt(fingerprint, view));
        return view;
    }

    private void add(String id, String name, String did, String license, String status, String qualification, String resources, String activity) {
        enterprises.put(id, new EnterpriseState(id, name, did, license, status, qualification, resources, activity, revision(id, status)));
    }

    private void require(Authentication auth, String capability) {
        if (!(auth instanceof JwtAuthenticationToken jwt)) throw new AccessDeniedException("platform capability required");
        Map<String, Object> claims = jwt.getToken().getClaims();
        Object memberships = claims.get("enterprise_memberships");
        if (memberships instanceof Collection<?> values && !values.isEmpty()
                && !"USER".equals(String.valueOf(claims.get("principal_type")))) {
            throw new AccessDeniedException("platform identity required");
        }
        Object claim = claims.get("platform_capabilities");
        if (!(claim instanceof Collection<?> values) || values.stream().noneMatch(value -> capability.equals(String.valueOf(value)))) {
            throw new AccessDeniedException("platform capability required");
        }
    }

    private static String revision(String id, String status) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest((id + ":" + status).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        for (byte item : bytes) value.append(String.format("%02x", item));
        return value.toString();
    }

    private record Receipt(String fingerprint, PlatformEnterpriseView view) {}

    private record EnterpriseState(String id, String name, String did, String license, String status, String qualification, String resources, String activity, String revision) {
        EnterpriseState withStatus(String nextStatus, String nextActivity) {
            return new EnterpriseState(id, name, did, license, nextStatus, qualification, resources, nextActivity, PlatformEnterpriseDirectoryService.revision(id, nextStatus));
        }

        PlatformEnterpriseView view() {
            return new PlatformEnterpriseView(id, name, did, license, status, qualification, resources, activity, revision);
        }
    }
}
