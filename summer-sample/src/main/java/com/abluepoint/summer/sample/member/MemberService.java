package com.abluepoint.summer.sample.member;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

@Service
final class MemberService {
    private final Map<String, Map<String, MemberRecord>> records = new ConcurrentHashMap<>();
    private final Map<String, MutationReceipt> idempotency = new ConcurrentHashMap<>();
    private final Map<String, List<AuditEvent>> audit = new ConcurrentHashMap<>();

    MemberService() {
        records.put("ent-acme", new ConcurrentHashMap<>(Map.of(
                "member-alice", new MemberRecord("Alice Zhang", "alice@example.test", "ACTIVE"),
                "member-bob", new MemberRecord("Bob Li", "bob@example.test", "DISABLED")
        )));
    }

    MemberPage list(String enterpriseId, int page, int size, String query, String status, Authentication auth) {
        requireMembership(enterpriseId, auth);
        List<MemberSummary> all = records.getOrDefault(enterpriseId, Map.of()).entrySet().stream()
                .map(e -> new MemberSummary(e.getKey(), e.getValue().displayName, e.getValue().email, e.getValue().status))
                .filter(m -> query == null || query.isBlank() || m.displayName().toLowerCase().contains(query.toLowerCase()) || m.email().toLowerCase().contains(query.toLowerCase()))
                .filter(m -> status == null || status.isBlank() || status.equalsIgnoreCase(m.status()))
                .sorted(java.util.Comparator.comparing(MemberSummary::memberId))
                .toList();
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new MemberPage(enterpriseId, page, size, all.size(), all.subList(from, to), revision(enterpriseId));
    }

    MemberDetail detail(String enterpriseId, String memberId, Authentication auth) {
        requireMembership(enterpriseId, auth);
        MemberRecord record = records.getOrDefault(enterpriseId, Map.of()).get(memberId);
        if (record == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return detailOf(enterpriseId, memberId, record);
    }

    synchronized MemberDetail changeStatus(String enterpriseId, String memberId, String status, String ifMatch, String idempotencyKey, String requestId, Authentication auth) {
        requireMembership(enterpriseId, auth);
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IdempotencyConflictException("Idempotency-Key is required");
        String operationKey = enterpriseId + ":" + memberId + ":" + idempotencyKey;
        String fingerprint = status + "|" + ifMatch;
        MutationReceipt previous = idempotency.get(operationKey);
        if (previous != null) {
            if (!previous.fingerprint().equals(fingerprint)) throw new IdempotencyConflictException("Idempotency-Key was already used for another mutation");
            return previous.detail();
        }
        String currentRevision = quote(revision(enterpriseId));
        if (ifMatch == null || !ifMatch.equals(currentRevision)) {
            throw new PreconditionFailedException();
        }
        Map<String, MemberRecord> enterprise = records.getOrDefault(enterpriseId, Map.of());
        MemberRecord record = enterprise.get(memberId);
        if (record == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        String before = record.status;
        record.status = status;
        MemberDetail detail = detailOf(enterpriseId, memberId, record);
        idempotency.put(operationKey, new MutationReceipt(fingerprint, detail));
        String actor = auth.getName();
        audit.computeIfAbsent(enterpriseId + ":" + memberId, ignored -> new ArrayList<>())
                .add(new AuditEvent(actor, enterpriseId, memberId, "MEMBER_STATUS_CHANGED", before, status, requestId));
        return detail;
    }

    List<AuditEvent> audit(String enterpriseId, String memberId, Authentication auth) {
        requireMembership(enterpriseId, auth);
        return List.copyOf(audit.getOrDefault(enterpriseId + ":" + memberId, List.of()));
    }

    private void requireMembership(String enterpriseId, Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwt)) throw new AccessDeniedException("enterprise membership required");
        Object claim = jwt.getToken().getClaims().get("enterprise_memberships");
        if (!(claim instanceof Collection<?> values) || values.stream().noneMatch(v -> v instanceof Map<?, ?> m && enterpriseId.equals(String.valueOf(m.get("enterpriseId"))) && "ACTIVE".equalsIgnoreCase(String.valueOf(m.get("status"))))) {
            throw new AccessDeniedException("enterprise membership required");
        }
    }

    private MemberDetail detailOf(String enterpriseId, String memberId, MemberRecord r) {
        return new MemberDetail(enterpriseId, memberId, r.displayName, r.email, r.status, revision(enterpriseId));
    }

    private String revision(String enterpriseId) {
        try {
            String state = records.getOrDefault(enterpriseId, Map.of()).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + ":" + entry.getValue().displayName + ":" + entry.getValue().email + ":" + entry.getValue().status)
                    .reduce(enterpriseId, (left, right) -> left + "|" + right);
            return hex(MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String hex(byte[] bytes) { StringBuilder s = new StringBuilder(); for (byte b : bytes) s.append(String.format("%02x", b)); return s.toString(); }
    private static String quote(String value) { return "\"" + value + "\""; }
    record AuditEvent(String actorSubject, String enterpriseId, String memberId, String action, String beforeStatus, String afterStatus, String requestId) {}
    private record MutationReceipt(String fingerprint, MemberDetail detail) {}
    private static final class MemberRecord {
        private final String displayName;
        private final String email;
        private volatile String status;
        private MemberRecord(String displayName, String email, String status) { this.displayName = displayName; this.email = email; this.status = status; }
    }
}
