package com.abluepoint.summer.sample.invitation.application;

import com.abluepoint.summer.sample.invitation.domain.InvitationConflictException;
import com.abluepoint.summer.sample.invitation.domain.InvitationContextMismatchException;
import com.abluepoint.summer.sample.invitation.domain.InvitationValidationException;
import com.abluepoint.summer.sample.invitation.domain.InvitationPreconditionException;
import com.abluepoint.summer.sample.invitation.domain.InvitationIdempotencyConflictException;
import com.abluepoint.summer.sample.invitation.domain.JoinApplicationConflictException;
import com.abluepoint.summer.sample.invitation.infrastructure.InvitationReceipt;
import com.abluepoint.summer.sample.invitation.infrastructure.InvitationVersionedEntity;
import com.abluepoint.summer.sample.invitation.infrastructure.InvitationEntity;
import com.abluepoint.summer.sample.invitation.infrastructure.JoinApplicationEntity;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import com.abluepoint.summer.sample.invitation.domain.InvitationRules;
import com.abluepoint.summer.sample.invitation.infrastructure.*;
import static com.abluepoint.summer.sample.invitation.infrastructure.InvitationAccess.*;
import static com.abluepoint.summer.sample.invitation.infrastructure.InvitationTokens.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class InvitationService {
    private final InvitationStore store = new InvitationStore();
    private final com.abluepoint.summer.sample.member.infrastructure.MemberDirectory memberDirectory;
    private final com.abluepoint.summer.sample.organization.application.OrganizationMembershipProvisioner organizationMemberships;
    private final com.abluepoint.summer.sample.enterprise.application.EnterpriseRegistry enterprises;
    InvitationService(com.abluepoint.summer.sample.member.infrastructure.MemberDirectory memberDirectory,
                      com.abluepoint.summer.sample.organization.application.OrganizationMembershipProvisioner organizationMemberships,
                      com.abluepoint.summer.sample.enterprise.application.EnterpriseRegistry enterprises) {
        this.memberDirectory = memberDirectory; this.organizationMemberships = organizationMemberships; this.enterprises = enterprises;
    }

    public synchronized InvitationIssueView create(String enterpriseId, InvitationCreateCommand request, String key, String requestId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.invitation.write");
        requireKey(key);
        String fingerprint = "create|" + request;
        InvitationReceipt old = store.idempotency.get(op("create:" + enterpriseId, key));
        if (old != null) return replay(old, fingerprint);
        if (request == null || request.inviteeEmail() == null || request.inviteeEmail().isBlank()) throw new InvitationValidationException("inviteeEmail is required");
        long ttl = request.expiresInSeconds() == null ? 86400L : request.expiresInSeconds();
        String id = "inv-" + UUID.randomUUID();
        String token = token();
        InvitationEntity record = new InvitationEntity(id, enterpriseId, request.inviteeEmail().trim(), request.organizationId(), request.roleIds(), request.capabilityCodes(), Instant.now().plusSeconds(ttl), hash(token));
        store.invitations.put(id, record); store.tokenIndex.put(record.tokenHash, id);
        InvitationIssueView result = issue(record, token);
        store.idempotency.put(op("create:" + enterpriseId, key), new InvitationReceipt(fingerprint, result));
        audit(enterpriseId, id, "INVITATION_CREATED", null, record.state, requestId, auth);
        return result;
    }

    public synchronized InvitationPageView<InvitationView> listInvitations(String enterpriseId, String search, String status, int page, int pageSize, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.invitation.read");
        List<InvitationView> filtered = store.invitations.values().stream()
                .filter(r -> enterpriseId.equals(r.enterpriseId))
                .filter(r -> search == null || search.isBlank() || r.inviteeEmail.contains(search) || r.id.contains(search))
                .filter(r -> status == null || status.isBlank() || status.equals(r.state))
                .map(InvitationService::view)
                .toList();
        return page(filtered, page, pageSize);
    }

    public synchronized InvitationPageView<JoinApplicationView> listApplications(String enterpriseId, String search, String status, int page, int pageSize, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.join-application.read");
        List<JoinApplicationView> filtered = store.applications.values().stream()
                .filter(r -> enterpriseId.equals(r.enterpriseId))
                .filter(r -> search == null || search.isBlank() || r.applicantEmail.contains(search) || r.id.contains(search))
                .filter(r -> status == null || status.isBlank() || status.equals(r.state))
                .map(InvitationService::view)
                .toList();
        return page(filtered, page, pageSize);
    }

    private static <T> InvitationPageView<T> page(List<T> values, int requestedPage, int requestedPageSize) {
        int page = Math.max(1, requestedPage);
        int pageSize = Math.max(1, Math.min(100, requestedPageSize));
        int from = Math.min(values.size(), (page - 1) * pageSize);
        int to = Math.min(values.size(), from + pageSize);
        return new InvitationPageView<>(values.subList(from, to), page, pageSize, values.size());
    }

    public synchronized InvitationView get(String enterpriseId, String id, Authentication auth) { requireAdmin(enterpriseId, auth, "enterprise.invitation.read"); return view(find(enterpriseId, id)); }
    public synchronized List<InvitationAuditView> audit(String enterpriseId, String id, Authentication auth) { requireAdmin(enterpriseId, auth, "enterprise.invitation.read"); List<InvitationAuditView> result = new ArrayList<>(store.audits.getOrDefault(enterpriseId + ":" + id, List.of())); Collections.reverse(result); return List.copyOf(result); }

    public synchronized InvitationIssueView resend(String enterpriseId, String id, String ifMatch, String key, String requestId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.invitation.write"); requireKey(key);
        String fingerprint = "resend|" + id + "|" + ifMatch; InvitationReceipt old = store.idempotency.get(op("resend:" + enterpriseId + ":" + id, key)); if (old != null) return replay(old, fingerprint);
        InvitationEntity r = find(enterpriseId, id); requireEtag(r, ifMatch); usable(r);
        store.tokenIndex.remove(r.tokenHash); String token = token(); r.tokenHash = hash(token); r.revision++;
        store.tokenIndex.put(r.tokenHash, r.id); InvitationIssueView result = issue(r, token); store.idempotency.put(op("resend:" + enterpriseId + ":" + id, key), new InvitationReceipt(fingerprint, result));
        audit(enterpriseId, id, "INVITATION_RESENT", "PENDING", r.state, requestId, auth); return result;
    }

    public synchronized InvitationView revoke(String enterpriseId, String id, String ifMatch, String key, String requestId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.invitation.write"); requireKey(key);
        String fingerprint = "revoke|" + id + "|" + ifMatch; InvitationReceipt old = store.idempotency.get(op("revoke:" + enterpriseId + ":" + id, key)); if (old != null) return replay(old, fingerprint);
        InvitationEntity r = find(enterpriseId, id); requireEtag(r, ifMatch); InvitationRules.requirePending(r.state, "Invitation is no longer pending");
        r.state = "REVOKED"; r.revision++; InvitationView result = view(r); store.idempotency.put(op("revoke:" + enterpriseId + ":" + id, key), new InvitationReceipt(fingerprint, result)); audit(enterpriseId, id, "INVITATION_REVOKED", "PENDING", r.state, requestId, auth); return result;
    }

    @org.springframework.transaction.annotation.Transactional
    public synchronized InvitationView accept(String token, InvitationContextCommand context, String key, String requestId, Authentication auth) { return consume(token, context, key, requestId, auth, true); }
    public synchronized InvitationView reject(String token, InvitationContextCommand context, String key, String requestId, Authentication auth) { return consume(token, context, key, requestId, auth, false); }

    private InvitationView consume(String token, InvitationContextCommand context, String key, String requestId, Authentication auth, boolean accept) {
        requireKey(key); InvitationEntity r = token == null ? null : store.invitations.get(store.tokenIndex.get(hash(token))); if (r == null) throw new InvitationConflictException("Invitation token is invalid");
        if (context != null && context.enterpriseId() != null && !r.enterpriseId.equals(context.enterpriseId())) throw new InvitationContextMismatchException("Invitation enterprise context does not match token");
        requireSubject(r.inviteeEmail, auth); String fingerprint = (accept ? "accept|" : "reject|") + r.id + "|" + auth.getName(); String scope = (accept ? "accept:" : "reject:") + r.enterpriseId + ":" + r.id; InvitationReceipt old = store.idempotency.get(op(scope, key)); if (old != null) return replay(old, fingerprint);
        usable(r); InvitationRules.requirePending(r.state, "Invitation has already been consumed");
        String before = r.state; String memberId = accept ? provisionMembership(r.enterpriseId, "member-" + shortHash(auth.getName() + r.id), auth.getName(), r.inviteeEmail, r.organizationId) : null; r.state = accept ? "ACCEPTED" : "REJECTED"; r.memberId = memberId; r.revision++;
        InvitationView result = view(r); store.idempotency.put(op(scope, key), new InvitationReceipt(fingerprint, result)); audit(r.enterpriseId, r.id, accept ? "INVITATION_ACCEPTED" : "INVITATION_REJECTED", before, r.state, requestId, auth); return result;
    }

    public synchronized JoinApplicationView submit(String enterpriseId, JoinApplicationSubmitCommand req, String key, String requestId, Authentication auth) {
        requireApplicant(req == null ? null : req.applicantEmail(), auth); requireKey(key);
        String fingerprint = "submit|" + auth.getName() + "|" + req; InvitationReceipt old = store.idempotency.get(op("submit:" + enterpriseId, key)); if (old != null) return replay(old, fingerprint);
        if (!enterprises.exists(enterpriseId) || req == null || req.applicantEmail() == null || req.applicantEmail().isBlank()) throw new InvitationValidationException("Invalid join application");
        String id = "join-" + UUID.randomUUID(); JoinApplicationEntity r = new JoinApplicationEntity(id, enterpriseId, req.applicantEmail().trim(), req.applicantName(), req.organizationId(), req.capabilityCodes(), req.materials()); r.applicantSubject = auth.getName(); store.applications.put(id, r);
        JoinApplicationView result = view(r); store.idempotency.put(op("submit:" + enterpriseId, key), new InvitationReceipt(fingerprint, result)); audit(enterpriseId, id, "JOIN_APPLICATION_SUBMITTED", null, r.state, requestId, auth); return result;
    }

    public synchronized JoinApplicationView supplement(String enterpriseId, String id, JoinApplicationSupplementCommand req, String ifMatch, String key, String requestId, Authentication auth) {
        requireKey(key); JoinApplicationEntity r = findApp(enterpriseId, id); requireApplicantSubject(r.applicantSubject, auth); String fingerprint = "supplement|" + id + "|" + req + "|" + ifMatch; String scope = "supplement:" + enterpriseId + ":" + id; InvitationReceipt old = store.idempotency.get(op(scope, key)); if (old != null) return replay(old, fingerprint); requireEtag(r, ifMatch);
        InvitationRules.requireEditable(r.state, "Join application is no longer editable");
        r.materials = req == null ? List.of() : req.materials(); r.state = "NEEDS_INFO"; r.revision++; JoinApplicationView result = view(r); store.idempotency.put(op(scope, key), new InvitationReceipt(fingerprint, result)); audit(enterpriseId, id, "JOIN_APPLICATION_SUPPLEMENTED", "PENDING", r.state, requestId, auth); return result;
    }

    @org.springframework.transaction.annotation.Transactional
    public synchronized JoinApplicationView approve(String enterpriseId, String id, String ifMatch, String key, String requestId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.join-application.review"); requireKey(key); String fingerprint = "approve|" + id + "|" + ifMatch; String scope = "approve:" + enterpriseId + ":" + id; InvitationReceipt old = store.idempotency.get(op(scope, key)); if (old != null) return replay(old, fingerprint);
        JoinApplicationEntity r = findApp(enterpriseId, id); InvitationRules.requireEditable(r.state, "Join application cannot be approved in current state"); requireEtag(r, ifMatch);
        String before = r.state; String memberId = provisionMembership(r.enterpriseId, "member-" + shortHash(r.applicantSubject + r.id), r.applicantSubject, r.applicantEmail, r.organizationId); r.state = "APPROVED"; r.memberId = memberId; r.revision++; JoinApplicationView result = view(r); store.idempotency.put(op(scope, key), new InvitationReceipt(fingerprint, result)); audit(enterpriseId, id, "JOIN_APPLICATION_APPROVED", before, r.state, requestId, auth); return result;
    }

    public synchronized JoinApplicationView reject(String enterpriseId, String id, String ifMatch, String key, String requestId, Authentication auth) { return decide(enterpriseId, id, ifMatch, key, requestId, auth, "REJECTED", "JOIN_APPLICATION_REJECTED"); }
    public synchronized JoinApplicationView withdraw(String enterpriseId, String id, String ifMatch, String key, String requestId, Authentication auth) {
        requireKey(key); JoinApplicationEntity r = findApp(enterpriseId, id); requireApplicantSubject(r.applicantSubject, auth); String fingerprint = "withdraw|" + id + "|" + ifMatch; String scope = "withdraw:" + enterpriseId + ":" + id; InvitationReceipt old = store.idempotency.get(op(scope, key)); if (old != null) return replay(old, fingerprint); requireEtag(r, ifMatch); InvitationRules.requireEditable(r.state, "Join application cannot be withdrawn");
        String before = r.state; r.state = "WITHDRAWN"; r.revision++; JoinApplicationView result = view(r); store.idempotency.put(op(scope, key), new InvitationReceipt(fingerprint, result)); audit(enterpriseId, id, "JOIN_APPLICATION_WITHDRAWN", before, r.state, requestId, auth); return result;
    }
    public synchronized JoinApplicationView getApplication(String enterpriseId, String id, Authentication auth) { requireAdmin(enterpriseId, auth, "enterprise.join-application.read"); return view(findApp(enterpriseId, id)); }
    public synchronized List<InvitationAuditView> applicationAudit(String enterpriseId, String id, Authentication auth) { requireAdmin(enterpriseId, auth, "enterprise.join-application.read"); List<InvitationAuditView> result = new ArrayList<>(store.audits.getOrDefault(enterpriseId + ":" + id, List.of())); Collections.reverse(result); return List.copyOf(result); }

    private JoinApplicationView decide(String enterpriseId, String id, String ifMatch, String key, String requestId, Authentication auth, String state, String action) {
        requireAdmin(enterpriseId, auth, "enterprise.join-application.review"); requireKey(key); String fingerprint = state + "|" + id + "|" + ifMatch; String scope = "decide:" + enterpriseId + ":" + id; InvitationReceipt old = store.idempotency.get(op(scope, key)); if (old != null) return replay(old, fingerprint); JoinApplicationEntity r = findApp(enterpriseId, id); requireEtag(r, ifMatch); InvitationRules.requireEditable(r.state, "Join application cannot be changed in current state"); String before = r.state; r.state = state; r.revision++; JoinApplicationView result = view(r); store.idempotency.put(op(scope, key), new InvitationReceipt(fingerprint, result)); audit(enterpriseId, id, action, before, r.state, requestId, auth); return result;
    }

    private InvitationEntity find(String enterpriseId, String id) { if (!enterprises.exists(enterpriseId)) throw new InvitationConflictException("Enterprise not found"); InvitationEntity r = store.invitations.get(id); if (r == null || !enterpriseId.equals(r.enterpriseId)) throw new InvitationConflictException("Invitation not found"); return r; }
    private JoinApplicationEntity findApp(String enterpriseId, String id) { if (!enterprises.exists(enterpriseId)) throw new JoinApplicationConflictException("Join application not found"); JoinApplicationEntity r = store.applications.get(id); if (r == null || !enterpriseId.equals(r.enterpriseId)) throw new JoinApplicationConflictException("Join application not found"); return r; }
    private static void requireKey(String key) { if (key == null || key.isBlank()) throw new InvitationIdempotencyConflictException("Idempotency-Key is required"); }
    private static void requireEtag(InvitationVersionedEntity r, String etag) { if (etag == null || !etag.equals(r.etag())) throw new InvitationPreconditionException(); }
    private static void usable(InvitationEntity r) { if (InvitationRules.expired(r.expiresAt, Instant.now())) { r.state = "EXPIRED"; throw new InvitationConflictException("Invitation has expired"); } InvitationRules.requirePending(r.state, "Invitation is not usable"); }
    private static String op(String enterpriseId, String key) { return enterpriseId + ":" + key; }
    private String provisionMembership(String enterpriseId, String memberId, String subject, String email, String organizationId) {
        try {
            var member = memberDirectory.forSubject(enterpriseId, subject).orElseGet(() -> memberDirectory.provision(enterpriseId, memberId, subject, email, email));
            if (!"ACTIVE".equals(member.status())) throw new InvitationConflictException("Membership is not active");
            if (organizationId != null && !organizationId.isBlank()) organizationMemberships.assign(enterpriseId, member.memberId(), organizationId);
            return member.memberId();
        } catch (IllegalArgumentException | IllegalStateException failure) {
            throw new InvitationConflictException(failure.getMessage());
        }
    }
    @SuppressWarnings("unchecked") private static <T> T replay(InvitationReceipt r, String fp) { if (!r.fingerprint().equals(fp)) throw new InvitationIdempotencyConflictException("Idempotency-Key was already used for another mutation"); return (T) r.result(); }
    private static InvitationIssueView issue(InvitationEntity r, String token) { return new InvitationIssueView(r.id, r.enterpriseId, r.organizationId, r.roleIds, r.capabilityCodes, r.expiresAt.toString(), r.state, token, r.etagValue()); }
    private static InvitationView view(InvitationEntity r) { return new InvitationView(r.id, r.enterpriseId, r.inviteeEmail, r.organizationId, r.roleIds, r.capabilityCodes, r.expiresAt.toString(), r.state, r.memberId, r.etagValue()); }
    private static JoinApplicationView view(JoinApplicationEntity r) { return new JoinApplicationView(r.id, r.enterpriseId, r.applicantEmail, r.applicantName, r.organizationId, r.capabilityCodes, r.materials, r.state, r.memberId, r.etagValue()); }
    private void audit(String enterpriseId, String id, String action, String before, String after, String requestId, Authentication auth) { store.audits.computeIfAbsent(enterpriseId + ":" + id, ignored -> new ArrayList<>()).add(new InvitationAuditView(auth == null ? "" : auth.getName(), enterpriseId, id, action, before, after, requestId)); }


}
