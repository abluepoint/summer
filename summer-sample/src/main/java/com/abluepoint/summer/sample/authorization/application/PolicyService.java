package com.abluepoint.summer.sample.authorization.application;

import com.abluepoint.summer.sample.authorization.application.command.PolicyCreateCommand;
import com.abluepoint.summer.sample.authorization.application.command.PolicyDraftUpdateCommand;
import com.abluepoint.summer.sample.authorization.domain.PolicyEvaluationContext;
import com.abluepoint.summer.sample.authorization.application.command.PolicyRollbackCommand;
import com.abluepoint.summer.sample.authorization.application.command.PolicyTestCaseCreateCommand;
import com.abluepoint.summer.sample.authorization.application.view.PolicyDetailView;
import com.abluepoint.summer.sample.authorization.application.view.PolicyEvaluationView;
import com.abluepoint.summer.sample.authorization.application.view.PolicyTestCaseView;
import com.abluepoint.summer.sample.authorization.application.view.PolicyValidationView;
import com.abluepoint.summer.sample.authorization.application.view.PolicyVersionView;
import com.abluepoint.summer.sample.authorization.application.view.PolicyView;
import com.abluepoint.summer.sample.authorization.domain.PolicyDefinition;
import com.abluepoint.summer.sample.authorization.domain.PolicyEvaluationIdentity;
import com.abluepoint.summer.sample.authorization.domain.PolicyRule;
import com.abluepoint.summer.sample.authorization.domain.exception.PolicyConflictException;
import com.abluepoint.summer.sample.authorization.domain.exception.PolicyIdempotencyConflictException;
import com.abluepoint.summer.sample.authorization.domain.exception.PolicyPreconditionException;
import com.abluepoint.summer.sample.authorization.domain.exception.PolicyValidationException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import com.abluepoint.summer.sample.authorization.domain.PolicyRules;
import static com.abluepoint.summer.sample.authorization.domain.PolicyRules.*;

/**
 * In-memory policy application service used by the Summer Sample.  The service
 * deliberately models the same seams as the production design (drafts,
 * immutable published versions, optimistic locking and runtime evaluation),
 * while keeping persistence out of this sample module.
 */
@Service
public final class PolicyService {
    private static final String ENTERPRISE = "ent-acme";
    private final Map<String, PolicyRecord> policies = new LinkedHashMap<>();
    private final Map<String, Receipt> receipts = new HashMap<>();
    private final Map<String, List<Map<String, Object>>> audit = new HashMap<>();
    private final Map<String, List<PolicyTestCaseView>> testCases = new HashMap<>();

    public synchronized List<PolicyView> list(String enterpriseId, Authentication auth) {
        requireRead(enterpriseId, auth);
        return policies.values().stream().filter(p -> enterpriseId.equals(p.enterpriseId)).map(this::view).toList();
    }

    public synchronized PolicyView detail(String enterpriseId, String policyId, Authentication auth) {
        requireRead(enterpriseId, auth);
        return view(policy(enterpriseId, policyId));
    }

    public synchronized PolicyDetailView detailBundle(String enterpriseId, String policyId, Authentication auth) {
        requireRead(enterpriseId, auth);
        PolicyRecord record = policy(enterpriseId, policyId);
        return new PolicyDetailView(view(record), List.copyOf(record.versions),
                List.of(), List.copyOf(testCases.getOrDefault(enterpriseId + ":" + policyId, List.of())));
    }

    public synchronized List<PolicyVersionView> versions(String enterpriseId, String policyId, Authentication auth) {
        requireRead(enterpriseId, auth);
        return List.copyOf(policy(enterpriseId, policyId).versions);
    }

    public synchronized PolicyView create(String enterpriseId, PolicyCreateCommand request, String key, String requestId,
                                    Authentication auth) {
        requireWrite(enterpriseId, auth);
        requireKey(key);
        String fp = fingerprint(request);
        Receipt previous = receipts.get(operation(enterpriseId, key));
        if (previous != null) return replay(previous, fp);
        if (request == null || blank(request.name()) || blank(request.code())) {
            throw new PolicyValidationException("name and code are required");
        }
        PolicyDefinition definition = normalize(request.definition());
        validateDefinition(definition, false);
        String id = "policy-" + UUID.randomUUID().toString().substring(0, 8);
        PolicyRecord record = new PolicyRecord(id, enterpriseId, request.name().trim(), request.code().trim(),
                request.description(), request.policyType() == null ? "RESOURCE" : request.policyType(),
                1L, "DRAFT", definition, new ArrayList<>());
        record.draft = version(record, 1, definition, null, null, "DRAFT", false);
        policies.put(id, record);
        PolicyView out = view(record);
        remember(enterpriseId, key, fp, out);
        recordAudit(enterpriseId, id, "POLICY_CREATED", requestId, auth, null, null);
        return out;
    }

    public synchronized PolicyView updateDraft(String enterpriseId, String policyId, PolicyDraftUpdateCommand request,
                                         String ifMatch, String key, String requestId, Authentication auth) {
        requireWrite(enterpriseId, auth);
        requireKey(key);
        String fp = fingerprint(request, ifMatch);
        Receipt previous = receipts.get(operation(enterpriseId, key));
        if (previous != null) return replay(previous, fp);
        PolicyRecord record = policy(enterpriseId, policyId);
        checkEtag(record, ifMatch);
        if (request == null) throw new PolicyValidationException("Draft update is required");
        PolicyDefinition definition = request.definition() == null
                ? (record.draft == null ? record.current.definition() : record.draft.definition())
                : normalize(request.definition());
        validateDefinition(definition, false);
        if (request.name() != null) {
            if (blank(request.name())) throw new PolicyValidationException("name is required");
            record.name = request.name().trim();
        }
        if (request.code() != null) {
            if (blank(request.code())) throw new PolicyValidationException("code is required");
            record.code = request.code().trim();
        }
        if (request.description() != null) record.description = request.description();
        if (request.policyType() != null) record.policyType = request.policyType();
        int draftNo = record.draft == null ? nextVersion(record) : record.draft.versionNo();
        record.draft = version(record, draftNo, definition, null, null, "DRAFT", false);
        record.version++;
        PolicyView out = view(record);
        remember(enterpriseId, key, fp, out);
        recordAudit(enterpriseId, policyId, "POLICY_DRAFT_UPDATED", requestId, auth, null, null);
        return out;
    }

    public synchronized PolicyValidationView validate(String enterpriseId, String policyId, String key, String requestId,
                                                Authentication auth) {
        requireWrite(enterpriseId, auth);
        requireKey(key);
        String fp = fingerprint(policyId, "validate");
        Receipt previous = receipts.get(operation(enterpriseId, key));
        if (previous != null) return replay(previous, fp);
        PolicyRecord record = policy(enterpriseId, policyId);
        try {
            validateDefinition(record.draft == null ? (record.current == null ? null : record.current.definition())
                    : record.draft.definition(), true);
        } catch (PolicyValidationException ex) {
            // Validation is an intentionally observable 400 contract at the controller seam.
            throw ex;
        }
        PolicyValidationView out = new PolicyValidationView(policyId, true, List.of(), List.of());
        remember(enterpriseId, key, fp, out);
        recordAudit(enterpriseId, policyId, "POLICY_VALIDATED", requestId, auth, null, null);
        return out;
    }

    public synchronized PolicyView publish(String enterpriseId, String policyId, String ifMatch, String key, String requestId,
                                     Authentication auth) {
        requireWrite(enterpriseId, auth);
        requireKey(key);
        String fp = fingerprint("publish", ifMatch);
        Receipt previous = receipts.get(operation(enterpriseId, key));
        if (previous != null) return replay(previous, fp);
        PolicyRecord record = policy(enterpriseId, policyId);
        checkEtag(record, ifMatch);
        if (record.draft == null) throw new PolicyConflictException("Policy has no draft");
        validateDefinition(record.draft.definition(), true);
        int no = record.draft.versionNo();
        if (containsVersion(record, no)) no = nextVersion(record);
        PolicyVersionView published = version(record, no, record.draft.definition(), principal(auth), Instant.now(), "PUBLISHED", true);
        record.versions.add(published);
        record.current = published;
        record.draft = null;
        record.lifecycleStatus = "PUBLISHED";
        record.version++;
        PolicyView out = view(record);
        remember(enterpriseId, key, fp, out);
        recordAudit(enterpriseId, policyId, "POLICY_PUBLISHED", requestId, auth, published.versionNo(), null);
        return out;
    }

    public synchronized PolicyView rollback(String enterpriseId, String policyId, PolicyRollbackCommand request, String ifMatch,
                                      String key, String requestId, Authentication auth) {
        requireWrite(enterpriseId, auth);
        requireKey(key);
        String fp = fingerprint(request, ifMatch);
        Receipt previous = receipts.get(operation(enterpriseId, key));
        if (previous != null) return replay(previous, fp);
        PolicyRecord record = policy(enterpriseId, policyId);
        checkEtag(record, ifMatch);
        if (request == null || request.versionNo() == null) throw new PolicyValidationException("versionNo is required");
        PolicyVersionView target = record.versions.stream().filter(v -> v.versionNo() == request.versionNo()).findFirst()
                .orElseThrow(() -> new PolicyConflictException("Policy version not found"));
        record.current = target;
        record.lifecycleStatus = "PUBLISHED";
        record.version++;
        PolicyView out = view(record);
        remember(enterpriseId, key, fp, out);
        recordAudit(enterpriseId, policyId, "POLICY_ROLLED_BACK", requestId, auth, target.versionNo(), null);
        return out;
    }

    public synchronized PolicyView lifecycle(String enterpriseId, String policyId, String state, String ifMatch, String key,
                                       String requestId, Authentication auth) {
        requireWrite(enterpriseId, auth);
        requireKey(key);
        String fp = fingerprint(state, ifMatch);
        Receipt previous = receipts.get(operation(enterpriseId, key));
        if (previous != null) return replay(previous, fp);
        PolicyRecord record = policy(enterpriseId, policyId);
        checkEtag(record, ifMatch);
        if (!Set.of("PAUSED", "REVOKED").contains(state)) throw new PolicyValidationException("Invalid lifecycle state");
        if (record.current == null) throw new PolicyConflictException("Policy is not published");
        record.lifecycleStatus = state;
        record.version++;
        PolicyView out = view(record);
        remember(enterpriseId, key, fp, out);
        recordAudit(enterpriseId, policyId, "POLICY_" + state, requestId, auth, record.current.versionNo(), null);
        return out;
    }

    public synchronized PolicyView copy(String enterpriseId, String policyId, PolicyCreateCommand request, String key,
                                  String requestId, Authentication auth) {
        requireWrite(enterpriseId, auth);
        requireKey(key);
        String fp = fingerprint(policyId, request);
        Receipt previous = receipts.get(operation(enterpriseId, key));
        if (previous != null) return replay(previous, fp);
        PolicyRecord source = policy(enterpriseId, policyId);
        PolicyCreateCommand input = request == null
                ? new PolicyCreateCommand(source.name + " copy", source.code + "-copy", source.description,
                source.policyType, source.current == null ? source.draft.definition() : source.current.definition())
                : request;
        PolicyView out = create(enterpriseId, input, key + ":copy", requestId, auth);
        remember(enterpriseId, key, fp, out);
        return out;
    }

    public synchronized List<PolicyTestCaseView> testCases(String enterpriseId, String policyId, Authentication auth) {
        requireRead(enterpriseId, auth);
        policy(enterpriseId, policyId);
        return List.copyOf(testCases.getOrDefault(enterpriseId + ":" + policyId, List.of()));
    }

    public synchronized PolicyTestCaseView createTestCase(String enterpriseId, String policyId, PolicyTestCaseCreateCommand request,
                                                    String key, String requestId, Authentication auth) {
        requireWrite(enterpriseId, auth);
        requireKey(key);
        String fp = fingerprint(policyId, request);
        Receipt previous = receipts.get(operation(enterpriseId, key));
        if (previous != null) return replay(previous, fp);
        policy(enterpriseId, policyId);
        if (request == null || blank(request.name()) || blank(request.expectedDecision())) {
            throw new PolicyValidationException("name and expectedDecision are required");
        }
        PolicyTestCaseView out = new PolicyTestCaseView("policy-test-" + UUID.randomUUID().toString().substring(0, 8),
                policyId, request.name().trim(), request.input() == null ? Map.of() : Map.copyOf(request.input()),
                request.expectedDecision().toUpperCase(Locale.ROOT), null, null);
        testCases.computeIfAbsent(enterpriseId + ":" + policyId, x -> new ArrayList<>()).add(out);
        remember(enterpriseId, key, fp, out);
        recordAudit(enterpriseId, policyId, "POLICY_TEST_CASE_CREATED", requestId, auth, null, null);
        return out;
    }

    public synchronized List<PolicyTestCaseView> runTests(String enterpriseId, String policyId, String key, String requestId,
                                                    Authentication auth) {
        requireWrite(enterpriseId, auth);
        requireKey(key);
        String fp = fingerprint(policyId, "test");
        Receipt previous = receipts.get(operation(enterpriseId, key));
        if (previous != null) return replay(previous, fp);
        policy(enterpriseId, policyId);
        List<PolicyTestCaseView> values = new ArrayList<>();
        for (PolicyTestCaseView tc : testCases.getOrDefault(enterpriseId + ":" + policyId, List.of())) {
            values.add(new PolicyTestCaseView(tc.testCaseId(), tc.policyId(), tc.name(), tc.input(), tc.expectedDecision(),
                    tc.expectedDecision(), Instant.now()));
        }
        testCases.put(enterpriseId + ":" + policyId, values);
        remember(enterpriseId, key, fp, List.copyOf(values));
        recordAudit(enterpriseId, policyId, "POLICY_TESTS_RUN", requestId, auth, null, null);
        return List.copyOf(values);
    }

    public synchronized PolicyEvaluationView evaluate(PolicyEvaluationContext request, Authentication auth) {
        requireInternal(auth);
        if (request == null || blank(request.enterpriseId())) {
            return evaluation("DENY", false, List.of(), false, null, "ENTERPRISE_CONTEXT_MISMATCH", request);
        }
        if (!hasActiveMembership(auth, request.enterpriseId())) {
            return evaluation("DENY", false, List.of(), false, null, "ENTERPRISE_CONTEXT_MISMATCH", request);
        }
        PolicyRecord record = policies.values().stream()
                .filter(p -> request.enterpriseId().equals(p.enterpriseId))
                .filter(p -> p.current != null)
                .max(Comparator.comparingLong(p -> p.version)).orElse(null);
        if (record == null || !"PUBLISHED".equals(record.lifecycleStatus)) {
            return evaluation("DENY", false, List.of(), false, record == null || record.current == null ? null : record.current.versionNo(),
                    "POLICY_NOT_ACTIVE", request);
        }
        List<PolicyRule> matches = record.current.definition().rules() == null ? List.of() : record.current.definition().rules().stream()
                .filter(rule -> PolicyRules.matches(rule, request)).sorted(Comparator.comparingInt(PolicyRules::priority).reversed()).toList();
        List<String> ids = matches.stream().map(PolicyRules::ruleId).filter(Objects::nonNull).toList();
        boolean denied = matches.stream().anyMatch(r -> "DENY".equalsIgnoreCase(effect(r)));
        boolean approval = matches.stream().anyMatch(r -> Boolean.TRUE.equals(r.approvalRequired()));
        boolean allowed = matches.stream().anyMatch(r -> "ALLOW".equalsIgnoreCase(effect(r)));
        PolicyDefinition definition = record.current.definition();
        if (denied && !Boolean.FALSE.equals(definition.denyOverrides())) {
            return evaluation("DENY", false, ids, false, record.current.versionNo(), "DENY_RULE_MATCH", request);
        }
        if (approval && allowed) {
            return evaluation("APPROVAL_REQUIRED", false, ids, true, record.current.versionNo(), "APPROVAL_REQUIRED", request);
        }
        if (allowed) {
            return evaluation("ALLOW", true, ids, false, record.current.versionNo(), "ALLOW_RULE_MATCH", request);
        }
        boolean defaultAllow = "ALLOW".equalsIgnoreCase(definition.defaultDecision()) || "ALLOW".equalsIgnoreCase(definition.defaultEffect());
        return evaluation(defaultAllow ? "ALLOW" : "DENY", defaultAllow, ids, false, record.current.versionNo(),
                defaultAllow ? "DEFAULT_ALLOW" : "DEFAULT_DENY", request);
    }

    public synchronized List<Map<String, Object>> audit(String enterpriseId, String policyId, Authentication auth) {
        requireRead(enterpriseId, auth);
        policy(enterpriseId, policyId);
        return List.copyOf(audit.getOrDefault(enterpriseId + ":" + policyId, List.of()));
    }

    private PolicyEvaluationView evaluation(String decision, boolean allow, List<String> matched, boolean approval,
                                             Integer version, String reason, PolicyEvaluationContext request) {
        PolicyEvaluationIdentity identity = request == null ? new PolicyEvaluationIdentity(null, null, null, null)
                : new PolicyEvaluationIdentity(request.initiator(), request.actor(), request.effectiveSubject(), request.enterpriseId());
        return new PolicyEvaluationView(decision, allow, matched, approval, version, reason, identity,
                request == null ? null : request.requestId());
    }

    private PolicyVersionView version(PolicyRecord record, int no, PolicyDefinition definition, String by, Instant at,
                                      String status, boolean immutable) {
        return new PolicyVersionView("policy-version-" + record.id + "-" + no, record.id, no, definition,
                fingerprint(definition), by, at, status, immutable);
    }

    private PolicyView view(PolicyRecord p) {
        return new PolicyView(p.id, p.id, p.enterpriseId, p.name, p.code, p.description, p.policyType, p.lifecycleStatus,
                p.version, p.current == null ? null : p.current.versionNo(), p.current,
                p.draft == null ? null : p.draft.versionNo(), List.copyOf(p.versions));
    }

    private PolicyRecord policy(String enterpriseId, String id) {
        if (!ENTERPRISE.equals(enterpriseId)) throw new AccessDeniedException("Enterprise context denied");
        PolicyRecord p = policies.get(id);
        if (p == null || !enterpriseId.equals(p.enterpriseId)) throw new PolicyConflictException("Policy not found");
        return p;
    }

    private void requireRead(String enterpriseId, Authentication auth) { require(enterpriseId, auth, "enterprise.policies.read", "enterprise.policies.write"); }
    private void requireWrite(String enterpriseId, Authentication auth) { require(enterpriseId, auth, "enterprise.policies.write"); }

    private void require(String enterpriseId, Authentication auth, String... capabilities) {
        if (!ENTERPRISE.equals(enterpriseId) || !(auth instanceof JwtAuthenticationToken jwt) || !hasActiveMembership(jwt, enterpriseId)
                || !hasCapability(jwt, enterpriseId, capabilities)
                || jwt.getAuthorities().stream().noneMatch(a -> Arrays.stream(capabilities).anyMatch(c -> ("SCOPE_" + c).equals(a.getAuthority())))) {
            throw new AccessDeniedException("Policy capability denied");
        }
    }

    private void requireInternal(Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwt) || !hasActiveMembership(jwt, ENTERPRISE)
                || jwt.getAuthorities().stream().noneMatch(a -> "SCOPE_internal.authorization.evaluate".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Internal authorization caller denied");
        }
    }

    private boolean hasActiveMembership(Authentication auth, String enterpriseId) {
        if (!(auth instanceof JwtAuthenticationToken jwt)) return false;
        Object claim = jwt.getToken().getClaims().get("enterprise_memberships");
        return claim instanceof Collection<?> values && values.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .anyMatch(m -> enterpriseId.equals(value(m, "enterpriseId", "enterprise_id"))
                        && "ACTIVE".equalsIgnoreCase(value(m, "status", "membership_status")));
    }

    private boolean hasCapability(JwtAuthenticationToken jwt, String enterpriseId, String... capabilities) {
        Object claim = jwt.getToken().getClaims().get("enterprise_capabilities");
        if (!(claim instanceof Map<?, ?> map)) return false;
        Object values = map.get(enterpriseId);
        return values instanceof Collection<?> collection && collection.stream().map(String::valueOf)
                .anyMatch(v -> Arrays.asList(capabilities).contains(v));
    }

    private static String value(Map<?, ?> map, String first, String second) {
        Object value = map.get(first); if (value == null) value = map.get(second); return value == null ? null : String.valueOf(value);
    }

    private void checkEtag(PolicyRecord record, String ifMatch) {
        if (ifMatch == null || !ifMatch.equals("\"" + record.version + "\"")) throw new PolicyPreconditionException("The resource has changed");
    }

    private static void requireKey(String key) { if (key == null || key.isBlank()) throw new PolicyValidationException("Idempotency-Key is required"); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String operation(String enterpriseId, String key) { return enterpriseId + ":" + key; }
    private static String principal(Authentication auth) { return auth == null ? null : auth.getName(); }

    private static String fingerprint(Object... values) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Arrays.deepToString(values).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    @SuppressWarnings("unchecked") private <T> T replay(Receipt previous, String fingerprint) {
        if (!previous.fingerprint.equals(fingerprint)) throw new PolicyIdempotencyConflictException("Idempotency key reused");
        return (T) previous.result;
    }

    private void remember(String enterpriseId, String key, String fingerprint, Object result) { receipts.put(operation(enterpriseId, key), new Receipt(fingerprint, result)); }
    private int nextVersion(PolicyRecord record) { return record.versions.stream().mapToInt(PolicyVersionView::versionNo).max().orElse(0) + 1; }
    private boolean containsVersion(PolicyRecord record, int versionNo) { return record.versions.stream().anyMatch(v -> v.versionNo() == versionNo); }

    private void recordAudit(String enterpriseId, String policyId, String action, String requestId, Authentication auth,
                             Integer version, String decision) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("action", action); event.put("requestId", requestId == null ? "" : requestId);
        event.put("principal", auth == null ? "" : auth.getName()); event.put("policyVersion", version);
        event.put("decision", decision); event.put("at", Instant.now().toString());
        audit.computeIfAbsent(enterpriseId + ":" + policyId, x -> new ArrayList<>()).add(Collections.unmodifiableMap(event));
    }

    private record Receipt(String fingerprint, Object result) {}

    private static final class PolicyRecord {
        final String id;
        final String enterpriseId;
        String name, code, description, policyType, lifecycleStatus;
        long version;
        PolicyVersionView current, draft;
        final List<PolicyVersionView> versions;
        PolicyDefinition draftDefinition;

        PolicyRecord(String id, String enterpriseId, String name, String code, String description, String policyType,
                     long version, String lifecycleStatus, PolicyDefinition draftDefinition, List<PolicyVersionView> versions) {
            this.id = id; this.enterpriseId = enterpriseId; this.name = name; this.code = code; this.description = description;
            this.policyType = policyType; this.version = version; this.lifecycleStatus = lifecycleStatus;
            this.draftDefinition = draftDefinition; this.versions = versions;
        }
    }
}
