package com.abluepoint.summer.sample.member;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.enterprise.DirectoryErrorCode;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
class MemberService {
    private final MemberDirectory directory;
    private final com.abluepoint.summer.sample.enterprise.EnterpriseRegistry enterprises;
    private final com.abluepoint.summer.sample.enterprise.DirectoryJournal journal;
    private final com.abluepoint.summer.sample.identity.LocalIdentityDirectory identities;
    MemberService(MemberDirectory directory, com.abluepoint.summer.sample.enterprise.EnterpriseRegistry enterprises, com.abluepoint.summer.sample.enterprise.DirectoryJournal journal, com.abluepoint.summer.sample.identity.LocalIdentityDirectory identities) {
        this.journal = journal;
        this.identities = identities;
        this.directory = directory; this.enterprises = enterprises;
    }

    @org.springframework.transaction.annotation.Transactional
    MemberDetail create(String enterpriseId, MemberCreateRequest request, String key, String requestId, Authentication auth) {
        requireMembership(enterpriseId, auth);
        if (key == null || key.isBlank() || key.length() > 120) throw SummerException.withMessage(DirectoryErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency-Key is required");
        enterprises.lock(enterpriseId);
        String fingerprint = request.toString();
        var prior = journal.find(enterpriseId, "member.create", key).orElse(null);
        if (prior != null) {
            if (!fingerprint.equals(prior.fingerprint())) throw SummerException.withMessage(DirectoryErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency-Key was already used for another mutation");
            return journal.decode(prior, MemberDetail.class);
        }
        if (!identities.isActive(request.subject())) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "An existing active login identity is required");
        if (directory.forSubject(enterpriseId, request.subject()).isPresent()) throw SummerException.withMessage(MemberErrorCode.CONFLICT, "Subject already has a membership in this enterprise");
        String id = "member-" + java.util.UUID.randomUUID();
        var member = directory.provision(enterpriseId, id, request.subject(), request.displayName(), request.email());
        MemberDetail detail = detailOf(enterpriseId, id, member);
        journal.audit(enterpriseId, "member.audit", id, new AuditEvent(auth.getName(), enterpriseId, id, "MEMBER_CREATED", null, "ACTIVE", requestId));
        journal.remember(enterpriseId, "member.create", key, fingerprint, detail);
        return detail;
    }

    MemberPage list(String enterpriseId, int page, int size, String query, String status, Authentication auth) {
        requireMembership(enterpriseId, auth);
        if (page < 0 || size < 1 || size > 200) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "page must be non-negative and size between 1 and 200");
        List<MemberSummary> all = directory.list(enterpriseId).stream()
                .map(e -> new MemberSummary(e.memberId(), e.displayName(), e.email(), e.status()))
                .filter(m -> query == null || query.isBlank() || m.displayName().toLowerCase().contains(query.toLowerCase()) || m.email().toLowerCase().contains(query.toLowerCase()))
                .filter(m -> status == null || status.isBlank() || status.equalsIgnoreCase(m.status()))
                .sorted(java.util.Comparator.comparing(MemberSummary::memberId))
                .toList();
        int from = (int) Math.min((long) page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new MemberPage(enterpriseId, page, size, all.size(), all.subList(from, to), revision(enterpriseId));
    }

    MemberDetail detail(String enterpriseId, String memberId, Authentication auth) {
        requireMembership(enterpriseId, auth);
        MemberDirectory.Member record = directory.find(enterpriseId, memberId).orElse(null);
        if (record == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return detailOf(enterpriseId, memberId, record);
    }

    @org.springframework.transaction.annotation.Transactional
    synchronized MemberDetail changeStatus(String enterpriseId, String memberId, String status, String ifMatch, String idempotencyKey, String requestId, Authentication auth) {
        requireMembership(enterpriseId, auth);
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 120) throw SummerException.withMessage(DirectoryErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency-Key is required");
        String operationKey = enterpriseId + ":" + memberId + ":" + idempotencyKey;
        String fingerprint = status + "|" + ifMatch;
        enterprises.lock(enterpriseId);
        var previous = journal.find(enterpriseId, "member.mutation", operationKey).orElse(null);
        if (previous != null) {
            if (!previous.fingerprint().equals(fingerprint)) throw SummerException.withMessage(DirectoryErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency-Key was already used for another mutation");
            return journal.decode(previous, MemberDetail.class);
        }
        String currentRevision = quote(revision(enterpriseId));
        if (ifMatch == null || !ifMatch.equals(currentRevision)) {
            throw SummerException.withMessage(SampleTransportErrorCode.PRECONDITION_FAILED, "The resource has changed");
        }
        MemberDirectory.Member record = directory.find(enterpriseId, memberId).orElse(null);
        if (record == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        String before = record.status();
        directory.changeStatus(enterpriseId, memberId, status);
        MemberDetail detail = detailOf(enterpriseId, memberId, directory.find(enterpriseId, memberId).orElseThrow());
        journal.remember(enterpriseId, "member.mutation", operationKey, fingerprint, detail);
        String actor = auth.getName();
        journal.audit(enterpriseId, "member.audit", memberId, new AuditEvent(actor, enterpriseId, memberId, "MEMBER_STATUS_CHANGED", before, status, requestId));
        return detail;
    }

    List<AuditEvent> audit(String enterpriseId, String memberId, Authentication auth) {
        requireMembership(enterpriseId, auth);
        return journal.audit(enterpriseId, "member.audit", memberId, AuditEvent.class);
    }

    private void requireMembership(String enterpriseId, Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwt)) throw new AccessDeniedException("enterprise membership required");
        Object claim = jwt.getToken().getClaims().get("enterprise_memberships");
        if (!(claim instanceof Collection<?> values) || values.stream().noneMatch(v -> v instanceof Map<?, ?> m && enterpriseId.equals(String.valueOf(m.get("enterpriseId"))) && "ACTIVE".equalsIgnoreCase(String.valueOf(m.get("status"))))) {
            throw new AccessDeniedException("enterprise membership required");
        }
    }

    private MemberDetail detailOf(String enterpriseId, String memberId, MemberDirectory.Member r) {
        return new MemberDetail(enterpriseId, memberId, r.displayName(), r.email(), r.status(), revision(enterpriseId));
    }

    private String revision(String enterpriseId) {
        try {
            String state = enterpriseId + "|" + directory.list(enterpriseId);
            return hex(MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String hex(byte[] bytes) { StringBuilder s = new StringBuilder(); for (byte b : bytes) s.append(String.format("%02x", b)); return s.toString(); }
    private static String quote(String value) { return "\"" + value + "\""; }
    record AuditEvent(String actorSubject, String enterpriseId, String memberId, String action, String beforeStatus, String afterStatus, String requestId) {}
}

