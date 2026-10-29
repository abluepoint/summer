package com.abluepoint.summer.sample.member.application;

import com.abluepoint.summer.sample.member.domain.IdempotencyConflictException;
import com.abluepoint.summer.sample.member.infrastructure.MemberDirectory;
import com.abluepoint.summer.sample.member.domain.MemberConflictException;
import com.abluepoint.summer.sample.member.domain.PreconditionFailedException;

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
public class MemberService {
    private final MemberDirectory directory;
    private final com.abluepoint.summer.sample.enterprise.application.EnterpriseRegistry enterprises;
    private final com.abluepoint.summer.sample.enterprise.infrastructure.DirectoryJournal journal;
    private final com.abluepoint.summer.sample.identity.application.directory.LocalIdentityDirectory identities;
    MemberService(MemberDirectory directory, com.abluepoint.summer.sample.enterprise.application.EnterpriseRegistry enterprises, com.abluepoint.summer.sample.enterprise.infrastructure.DirectoryJournal journal, com.abluepoint.summer.sample.identity.application.directory.LocalIdentityDirectory identities) {
        this.journal = journal;
        this.identities = identities;
        this.directory = directory; this.enterprises = enterprises;
    }

    @org.springframework.transaction.annotation.Transactional
    public MemberDetailView create(String enterpriseId, MemberCreateCommand request, String key, String requestId, Authentication auth) {
        com.abluepoint.summer.sample.member.infrastructure.MemberAccess.requireMembership(enterpriseId, auth);
        if (key == null || key.isBlank() || key.length() > 120) throw new IdempotencyConflictException("Idempotency-Key is required");
        enterprises.lock(enterpriseId);
        String fingerprint = request.toString();
        var prior = journal.find(enterpriseId, "member.create", key).orElse(null);
        if (prior != null) {
            if (!fingerprint.equals(prior.fingerprint())) throw new IdempotencyConflictException("Idempotency-Key was already used for another mutation");
            return journal.decode(prior, MemberDetailView.class);
        }
        if (!identities.isActive(request.subject())) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "An existing active login identity is required");
        if (directory.forSubject(enterpriseId, request.subject()).isPresent()) throw new MemberConflictException();
        String id = "member-" + java.util.UUID.randomUUID();
        var member = directory.provision(enterpriseId, id, request.subject(), request.displayName(), request.email());
        MemberDetailView detail = detailOf(enterpriseId, id, member);
        journal.audit(enterpriseId, "member.audit", id, new MemberAuditView(auth.getName(), enterpriseId, id, "MEMBER_CREATED", null, "ACTIVE", requestId));
        journal.remember(enterpriseId, "member.create", key, fingerprint, detail);
        return detail;
    }

    public MemberPageView list(String enterpriseId, int page, int size, String query, String status, Authentication auth) {
        com.abluepoint.summer.sample.member.infrastructure.MemberAccess.requireMembership(enterpriseId, auth);
        if (page < 0 || size < 1 || size > 200) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "page must be non-negative and size between 1 and 200");
        List<MemberSummaryView> all = directory.list(enterpriseId).stream()
                .map(e -> new MemberSummaryView(e.memberId(), e.displayName(), e.email(), e.status()))
                .filter(m -> query == null || query.isBlank() || m.displayName().toLowerCase().contains(query.toLowerCase()) || m.email().toLowerCase().contains(query.toLowerCase()))
                .filter(m -> status == null || status.isBlank() || status.equalsIgnoreCase(m.status()))
                .sorted(java.util.Comparator.comparing(MemberSummaryView::memberId))
                .toList();
        int from = (int) Math.min((long) page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new MemberPageView(enterpriseId, page, size, all.size(), all.subList(from, to), revision(enterpriseId));
    }

    public MemberDetailView detail(String enterpriseId, String memberId, Authentication auth) {
        com.abluepoint.summer.sample.member.infrastructure.MemberAccess.requireMembership(enterpriseId, auth);
        MemberView record = directory.find(enterpriseId, memberId).orElse(null);
        if (record == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        return detailOf(enterpriseId, memberId, record);
    }

    @org.springframework.transaction.annotation.Transactional
    public synchronized MemberDetailView changeStatus(String enterpriseId, String memberId, String status, String ifMatch, String idempotencyKey, String requestId, Authentication auth) {
        com.abluepoint.summer.sample.member.infrastructure.MemberAccess.requireMembership(enterpriseId, auth);
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 120) throw new IdempotencyConflictException("Idempotency-Key is required");
        String operationKey = enterpriseId + ":" + memberId + ":" + idempotencyKey;
        String fingerprint = status + "|" + ifMatch;
        enterprises.lock(enterpriseId);
        var previous = journal.find(enterpriseId, "member.mutation", operationKey).orElse(null);
        if (previous != null) {
            if (!previous.fingerprint().equals(fingerprint)) throw new IdempotencyConflictException("Idempotency-Key was already used for another mutation");
            return journal.decode(previous, MemberDetailView.class);
        }
        com.abluepoint.summer.sample.member.domain.MemberRules.requireVersion(revision(enterpriseId), ifMatch);
        MemberView record = directory.find(enterpriseId, memberId).orElse(null);
        if (record == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        String before = record.status();
        directory.changeStatus(enterpriseId, memberId, status);
        MemberDetailView detail = detailOf(enterpriseId, memberId, directory.find(enterpriseId, memberId).orElseThrow());
        journal.remember(enterpriseId, "member.mutation", operationKey, fingerprint, detail);
        String actor = auth.getName();
        journal.audit(enterpriseId, "member.audit", memberId, new MemberAuditView(actor, enterpriseId, memberId, "MEMBER_STATUS_CHANGED", before, status, requestId));
        return detail;
    }

    public List<MemberAuditView> audit(String enterpriseId, String memberId, Authentication auth) {
        com.abluepoint.summer.sample.member.infrastructure.MemberAccess.requireMembership(enterpriseId, auth);
        return journal.audit(enterpriseId, "member.audit", memberId, MemberAuditView.class);
    }

    private MemberDetailView detailOf(String enterpriseId, String memberId, MemberView r) {
        return new MemberDetailView(enterpriseId, memberId, r.displayName(), r.email(), r.status(), revision(enterpriseId));
    }

    private String revision(String enterpriseId) {
        try {
            String state = enterpriseId + "|" + directory.list(enterpriseId);
            return hex(MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String hex(byte[] bytes) { StringBuilder s = new StringBuilder(); for (byte b : bytes) s.append(String.format("%02x", b)); return s.toString(); }
    private static String quote(String value) { return "\"" + value + "\""; }


}
