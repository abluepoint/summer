package com.abluepoint.summer.sample.invitation;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
class InvitationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, InvitationRecord> invitations = new LinkedHashMap<>();
    private final Map<String, String> tokenIndex = new HashMap<>();
    private final Map<String, JoinRecord> applications = new LinkedHashMap<>();
    private final com.abluepoint.summer.sample.member.infrastructure.MemberDirectory memberDirectory;
    private final com.abluepoint.summer.sample.organization.OrganizationMembershipProvisioner organizationMemberships;
    private final com.abluepoint.summer.sample.enterprise.application.EnterpriseRegistry enterprises;
    InvitationService(com.abluepoint.summer.sample.member.infrastructure.MemberDirectory memberDirectory,
                      com.abluepoint.summer.sample.organization.OrganizationMembershipProvisioner organizationMemberships,
                      com.abluepoint.summer.sample.enterprise.application.EnterpriseRegistry enterprises) {
        this.memberDirectory = memberDirectory; this.organizationMemberships = organizationMemberships; this.enterprises = enterprises;
    }
    private final Map<String, Receipt> idempotency = new HashMap<>();
    private final Map<String, List<AuditEvent>> audits = new HashMap<>();

    synchronized InvitationIssue create(String enterpriseId, InvitationCreateRequest request, String key, String requestId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.invitation.write");
        requireKey(key);
        String fingerprint = "create|" + request;
        Receipt old = idempotency.get(op("create:" + enterpriseId, key));
        if (old != null) return replay(old, fingerprint);
        if (request == null || request.inviteeEmail() == null || request.inviteeEmail().isBlank()) throw new InvitationValidationException("inviteeEmail is required");
        long ttl = request.expiresInSeconds() == null ? 86400L : request.expiresInSeconds();
        String id = "inv-" + UUID.randomUUID();
        String token = token();
        InvitationRecord record = new InvitationRecord(id, enterpriseId, request.inviteeEmail().trim(), request.organizationId(), request.roleIds(), request.capabilityCodes(), Instant.now().plusSeconds(ttl), hash(token));
        invitations.put(id, record); tokenIndex.put(record.tokenHash, id);
        InvitationIssue result = issue(record, token);
        idempotency.put(op("create:" + enterpriseId, key), new Receipt(fingerprint, result));
        audit(enterpriseId, id, "INVITATION_CREATED", null, record.state, requestId, auth);
        return result;
    }

    synchronized Page<InvitationView> listInvitations(String enterpriseId, String search, String status, int page, int pageSize, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.invitation.read");
        List<InvitationView> filtered = invitations.values().stream()
                .filter(r -> enterpriseId.equals(r.enterpriseId))
                .filter(r -> search == null || search.isBlank() || r.inviteeEmail.contains(search) || r.id.contains(search))
                .filter(r -> status == null || status.isBlank() || status.equals(r.state))
                .map(InvitationService::view)
                .toList();
        return page(filtered, page, pageSize);
    }

    synchronized Page<JoinApplicationView> listApplications(String enterpriseId, String search, String status, int page, int pageSize, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.join-application.read");
        List<JoinApplicationView> filtered = applications.values().stream()
                .filter(r -> enterpriseId.equals(r.enterpriseId))
                .filter(r -> search == null || search.isBlank() || r.applicantEmail.contains(search) || r.id.contains(search))
                .filter(r -> status == null || status.isBlank() || status.equals(r.state))
                .map(InvitationService::view)
                .toList();
        return page(filtered, page, pageSize);
    }

    private static <T> Page<T> page(List<T> values, int requestedPage, int requestedPageSize) {
        int page = Math.max(1, requestedPage);
        int pageSize = Math.max(1, Math.min(100, requestedPageSize));
        int from = Math.min(values.size(), (page - 1) * pageSize);
        int to = Math.min(values.size(), from + pageSize);
        return new Page<>(values.subList(from, to), page, pageSize, values.size());
    }

    synchronized InvitationView get(String enterpriseId, String id, Authentication auth) { requireAdmin(enterpriseId, auth, "enterprise.invitation.read"); return view(find(enterpriseId, id)); }
    synchronized List<AuditEvent> audit(String enterpriseId, String id, Authentication auth) { requireAdmin(enterpriseId, auth, "enterprise.invitation.read"); List<AuditEvent> result = new ArrayList<>(audits.getOrDefault(enterpriseId + ":" + id, List.of())); Collections.reverse(result); return List.copyOf(result); }

    synchronized InvitationIssue resend(String enterpriseId, String id, String ifMatch, String key, String requestId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.invitation.write"); requireKey(key);
        String fingerprint = "resend|" + id + "|" + ifMatch; Receipt old = idempotency.get(op("resend:" + enterpriseId + ":" + id, key)); if (old != null) return replay(old, fingerprint);
        InvitationRecord r = find(enterpriseId, id); requireEtag(r, ifMatch); usable(r);
        tokenIndex.remove(r.tokenHash); String token = token(); r.tokenHash = hash(token); r.revision++;
        tokenIndex.put(r.tokenHash, r.id); InvitationIssue result = issue(r, token); idempotency.put(op("resend:" + enterpriseId + ":" + id, key), new Receipt(fingerprint, result));
        audit(enterpriseId, id, "INVITATION_RESENT", "PENDING", r.state, requestId, auth); return result;
    }

    synchronized InvitationView revoke(String enterpriseId, String id, String ifMatch, String key, String requestId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.invitation.write"); requireKey(key);
        String fingerprint = "revoke|" + id + "|" + ifMatch; Receipt old = idempotency.get(op("revoke:" + enterpriseId + ":" + id, key)); if (old != null) return replay(old, fingerprint);
        InvitationRecord r = find(enterpriseId, id); requireEtag(r, ifMatch); if (!"PENDING".equals(r.state)) throw new InvitationConflictException("Invitation is no longer pending");
        r.state = "REVOKED"; r.revision++; InvitationView result = view(r); idempotency.put(op("revoke:" + enterpriseId + ":" + id, key), new Receipt(fingerprint, result)); audit(enterpriseId, id, "INVITATION_REVOKED", "PENDING", r.state, requestId, auth); return result;
    }

    @org.springframework.transaction.annotation.Transactional
    synchronized InvitationView accept(String token, InvitationContextRequest context, String key, String requestId, Authentication auth) { return consume(token, context, key, requestId, auth, true); }
    synchronized InvitationView reject(String token, InvitationContextRequest context, String key, String requestId, Authentication auth) { return consume(token, context, key, requestId, auth, false); }

    private InvitationView consume(String token, InvitationContextRequest context, String key, String requestId, Authentication auth, boolean accept) {
        requireKey(key); InvitationRecord r = token == null ? null : invitations.get(tokenIndex.get(hash(token))); if (r == null) throw new InvitationConflictException("Invitation token is invalid");
        if (context != null && context.enterpriseId() != null && !r.enterpriseId.equals(context.enterpriseId())) throw new InvitationContextMismatchException("Invitation enterprise context does not match token");
        requireSubject(r.inviteeEmail, auth); String fingerprint = (accept ? "accept|" : "reject|") + r.id + "|" + auth.getName(); String scope = (accept ? "accept:" : "reject:") + r.enterpriseId + ":" + r.id; Receipt old = idempotency.get(op(scope, key)); if (old != null) return replay(old, fingerprint);
        usable(r); if (!"PENDING".equals(r.state)) throw new InvitationConflictException("Invitation has already been consumed");
        String before = r.state; String memberId = accept ? provisionMembership(r.enterpriseId, "member-" + shortHash(auth.getName() + r.id), auth.getName(), r.inviteeEmail, r.organizationId) : null; r.state = accept ? "ACCEPTED" : "REJECTED"; r.memberId = memberId; r.revision++;
        InvitationView result = view(r); idempotency.put(op(scope, key), new Receipt(fingerprint, result)); audit(r.enterpriseId, r.id, accept ? "INVITATION_ACCEPTED" : "INVITATION_REJECTED", before, r.state, requestId, auth); return result;
    }

    synchronized JoinApplicationView submit(String enterpriseId, JoinApplicationSubmitRequest req, String key, String requestId, Authentication auth) {
        requireApplicant(req == null ? null : req.applicantEmail(), auth); requireKey(key);
        String fingerprint = "submit|" + auth.getName() + "|" + req; Receipt old = idempotency.get(op("submit:" + enterpriseId, key)); if (old != null) return replay(old, fingerprint);
        if (!enterprises.exists(enterpriseId) || req == null || req.applicantEmail() == null || req.applicantEmail().isBlank()) throw new InvitationValidationException("Invalid join application");
        String id = "join-" + UUID.randomUUID(); JoinRecord r = new JoinRecord(id, enterpriseId, req.applicantEmail().trim(), req.applicantName(), req.organizationId(), req.capabilityCodes(), req.materials()); r.applicantSubject = auth.getName(); applications.put(id, r);
        JoinApplicationView result = view(r); idempotency.put(op("submit:" + enterpriseId, key), new Receipt(fingerprint, result)); audit(enterpriseId, id, "JOIN_APPLICATION_SUBMITTED", null, r.state, requestId, auth); return result;
    }

    synchronized JoinApplicationView supplement(String enterpriseId, String id, JoinApplicationSupplementRequest req, String ifMatch, String key, String requestId, Authentication auth) {
        requireKey(key); JoinRecord r = findApp(enterpriseId, id); requireApplicantSubject(r.applicantSubject, auth); String fingerprint = "supplement|" + id + "|" + req + "|" + ifMatch; String scope = "supplement:" + enterpriseId + ":" + id; Receipt old = idempotency.get(op(scope, key)); if (old != null) return replay(old, fingerprint); requireEtag(r, ifMatch);
        if (!("PENDING".equals(r.state) || "NEEDS_INFO".equals(r.state))) throw new JoinApplicationConflictException("Join application is no longer editable");
        r.materials = req == null ? List.of() : req.materials(); r.state = "NEEDS_INFO"; r.revision++; JoinApplicationView result = view(r); idempotency.put(op(scope, key), new Receipt(fingerprint, result)); audit(enterpriseId, id, "JOIN_APPLICATION_SUPPLEMENTED", "PENDING", r.state, requestId, auth); return result;
    }

    @org.springframework.transaction.annotation.Transactional
    synchronized JoinApplicationView approve(String enterpriseId, String id, String ifMatch, String key, String requestId, Authentication auth) {
        requireAdmin(enterpriseId, auth, "enterprise.join-application.review"); requireKey(key); String fingerprint = "approve|" + id + "|" + ifMatch; String scope = "approve:" + enterpriseId + ":" + id; Receipt old = idempotency.get(op(scope, key)); if (old != null) return replay(old, fingerprint);
        JoinRecord r = findApp(enterpriseId, id); if (!("PENDING".equals(r.state) || "NEEDS_INFO".equals(r.state))) throw new JoinApplicationConflictException("Join application cannot be approved in current state"); requireEtag(r, ifMatch);
        String before = r.state; String memberId = provisionMembership(r.enterpriseId, "member-" + shortHash(r.applicantSubject + r.id), r.applicantSubject, r.applicantEmail, r.organizationId); r.state = "APPROVED"; r.memberId = memberId; r.revision++; JoinApplicationView result = view(r); idempotency.put(op(scope, key), new Receipt(fingerprint, result)); audit(enterpriseId, id, "JOIN_APPLICATION_APPROVED", before, r.state, requestId, auth); return result;
    }

    synchronized JoinApplicationView reject(String enterpriseId, String id, String ifMatch, String key, String requestId, Authentication auth) { return decide(enterpriseId, id, ifMatch, key, requestId, auth, "REJECTED", "JOIN_APPLICATION_REJECTED"); }
    synchronized JoinApplicationView withdraw(String enterpriseId, String id, String ifMatch, String key, String requestId, Authentication auth) {
        requireKey(key); JoinRecord r = findApp(enterpriseId, id); requireApplicantSubject(r.applicantSubject, auth); String fingerprint = "withdraw|" + id + "|" + ifMatch; String scope = "withdraw:" + enterpriseId + ":" + id; Receipt old = idempotency.get(op(scope, key)); if (old != null) return replay(old, fingerprint); requireEtag(r, ifMatch); if (!("PENDING".equals(r.state) || "NEEDS_INFO".equals(r.state))) throw new JoinApplicationConflictException("Join application cannot be withdrawn");
        String before = r.state; r.state = "WITHDRAWN"; r.revision++; JoinApplicationView result = view(r); idempotency.put(op(scope, key), new Receipt(fingerprint, result)); audit(enterpriseId, id, "JOIN_APPLICATION_WITHDRAWN", before, r.state, requestId, auth); return result;
    }
    synchronized JoinApplicationView getApplication(String enterpriseId, String id, Authentication auth) { requireAdmin(enterpriseId, auth, "enterprise.join-application.read"); return view(findApp(enterpriseId, id)); }
    synchronized List<AuditEvent> applicationAudit(String enterpriseId, String id, Authentication auth) { requireAdmin(enterpriseId, auth, "enterprise.join-application.read"); List<AuditEvent> result = new ArrayList<>(audits.getOrDefault(enterpriseId + ":" + id, List.of())); Collections.reverse(result); return List.copyOf(result); }

    private JoinApplicationView decide(String enterpriseId, String id, String ifMatch, String key, String requestId, Authentication auth, String state, String action) {
        requireAdmin(enterpriseId, auth, "enterprise.join-application.review"); requireKey(key); String fingerprint = state + "|" + id + "|" + ifMatch; String scope = "decide:" + enterpriseId + ":" + id; Receipt old = idempotency.get(op(scope, key)); if (old != null) return replay(old, fingerprint); JoinRecord r = findApp(enterpriseId, id); requireEtag(r, ifMatch); if (!("PENDING".equals(r.state) || "NEEDS_INFO".equals(r.state))) throw new JoinApplicationConflictException("Join application cannot be changed in current state"); String before = r.state; r.state = state; r.revision++; JoinApplicationView result = view(r); idempotency.put(op(scope, key), new Receipt(fingerprint, result)); audit(enterpriseId, id, action, before, r.state, requestId, auth); return result;
    }

    private InvitationRecord find(String enterpriseId, String id) { if (!enterprises.exists(enterpriseId)) throw new InvitationConflictException("Enterprise not found"); InvitationRecord r = invitations.get(id); if (r == null || !enterpriseId.equals(r.enterpriseId)) throw new InvitationConflictException("Invitation not found"); return r; }
    private JoinRecord findApp(String enterpriseId, String id) { if (!enterprises.exists(enterpriseId)) throw new JoinApplicationConflictException("Join application not found"); JoinRecord r = applications.get(id); if (r == null || !enterpriseId.equals(r.enterpriseId)) throw new JoinApplicationConflictException("Join application not found"); return r; }
    private static void requireKey(String key) { if (key == null || key.isBlank()) throw new InvitationIdempotencyConflictException("Idempotency-Key is required"); }
    private static void requireEtag(BaseRecord r, String etag) { if (etag == null || !etag.equals(r.etag())) throw new InvitationPreconditionException(); }
    private static void usable(InvitationRecord r) { if (r.expiresAt.isBefore(Instant.now())) { r.state = "EXPIRED"; throw new InvitationConflictException("Invitation has expired"); } if (!"PENDING".equals(r.state)) throw new InvitationConflictException("Invitation is not usable"); }
    private static void requireSubject(String email, Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwt) || email == null || email.isBlank()) throw new AccessDeniedException("Applicant identity does not match");
        boolean verifiedEmail = Boolean.TRUE.equals(jwt.getToken().getClaim("email_verified")) && email.equalsIgnoreCase(jwt.getToken().getClaimAsString("email"));
        if (!email.equalsIgnoreCase(auth.getName()) && !verifiedEmail) throw new AccessDeniedException("Verified applicant identity does not match");
    }
    private static void requireApplicantSubject(String subject, Authentication auth) {
        if (auth == null || subject == null || !subject.equals(auth.getName())) throw new AccessDeniedException("Applicant identity does not match");
    }
    private static void requireApplicant(String email, Authentication auth) { if (!(auth instanceof JwtAuthenticationToken)) throw new AccessDeniedException("Applicant authentication required"); requireSubject(email, auth); }
    private static void requireAdmin(String enterpriseId, Authentication auth, String capability) { if (!(auth instanceof JwtAuthenticationToken jwt) || !activeMember(jwt, enterpriseId) || !hasCapability(jwt, enterpriseId, capability)) throw new AccessDeniedException("Enterprise capability required"); }
    private static boolean activeMember(JwtAuthenticationToken jwt, String enterpriseId) { Object c = jwt.getToken().getClaims().get("enterprise_memberships"); return c instanceof Collection<?> col && col.stream().anyMatch(v -> { if (!(v instanceof Map<?, ?> m)) return false; Object enterprise = m.get("enterpriseId"); if (enterprise == null) enterprise = m.get("enterprise_id"); Object status = m.get("status"); if (status == null) status = m.get("membership_status"); return enterpriseId.equals(String.valueOf(enterprise)) && "ACTIVE".equalsIgnoreCase(String.valueOf(status)); }); }
    private static boolean hasCapability(JwtAuthenticationToken jwt, String enterpriseId, String capability) { Object c = jwt.getToken().getClaims().get("enterprise_capabilities"); if (!(c instanceof Map<?, ?> m)) return false; Object values = m.get(enterpriseId); return values instanceof Collection<?> col && col.stream().map(String::valueOf).anyMatch(capability::equals); }
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
    @SuppressWarnings("unchecked") private static <T> T replay(Receipt r, String fp) { if (!r.fingerprint.equals(fp)) throw new InvitationIdempotencyConflictException("Idempotency-Key was already used for another mutation"); return (T) r.result; }
    private static String token() { byte[] b = new byte[32]; RANDOM.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String shortHash(String value) { return hash(value).substring(0, 16); }
    private static InvitationIssue issue(InvitationRecord r, String token) { return new InvitationIssue(r.id, r.enterpriseId, r.organizationId, r.roleIds, r.capabilityCodes, r.expiresAt.toString(), r.state, token, r.etagValue()); }
    private static InvitationView view(InvitationRecord r) { return new InvitationView(r.id, r.enterpriseId, r.inviteeEmail, r.organizationId, r.roleIds, r.capabilityCodes, r.expiresAt.toString(), r.state, r.memberId, r.etagValue()); }
    private static JoinApplicationView view(JoinRecord r) { return new JoinApplicationView(r.id, r.enterpriseId, r.applicantEmail, r.applicantName, r.organizationId, r.capabilityCodes, r.materials, r.state, r.memberId, r.etagValue()); }
    private void audit(String enterpriseId, String id, String action, String before, String after, String requestId, Authentication auth) { audits.computeIfAbsent(enterpriseId + ":" + id, ignored -> new ArrayList<>()).add(new AuditEvent(auth == null ? "" : auth.getName(), enterpriseId, id, action, before, after, requestId)); }
    record AuditEvent(String actorSubject, String enterpriseId, String resourceId, String action, String beforeStatus, String afterStatus, String requestId) {}
    record Page<T>(List<T> items, int page, int pageSize, int total) {}
    private record Receipt(String fingerprint, Object result) {}
    private interface BaseRecord { String etag(); }
    private static final class InvitationRecord implements BaseRecord { final String id, enterpriseId, inviteeEmail, organizationId; final List<String> roleIds, capabilityCodes; final Instant expiresAt; String tokenHash, state = "PENDING", memberId; int revision = 1; InvitationRecord(String id,String e,String email,String org,List<String> roles,List<String> caps,Instant exp,String hash){this.id=id;enterpriseId=e;inviteeEmail=email;organizationId=org;roleIds=List.copyOf(roles);capabilityCodes=List.copyOf(caps);expiresAt=exp;tokenHash=hash;} String etagValue(){return "v"+revision;} public String etag(){return "\""+etagValue()+"\"";} }
    private static final class JoinRecord implements BaseRecord { final String id, enterpriseId, applicantEmail, applicantName, organizationId; final List<String> capabilityCodes; List<String> materials; String state = "PENDING", memberId, applicantSubject; int revision = 1; JoinRecord(String id,String e,String email,String name,String org,List<String> caps,List<String> mats){this.id=id;enterpriseId=e;applicantEmail=email;applicantName=name;organizationId=org;capabilityCodes=List.copyOf(caps);materials=new ArrayList<>(mats);} String etagValue(){return "v"+revision;} public String etag(){return "\""+etagValue()+"\"";} }
}
