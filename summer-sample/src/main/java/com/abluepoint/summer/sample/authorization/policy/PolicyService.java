package com.abluepoint.summer.sample.authorization.policy;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import com.abluepoint.summer.sample.authorization.AuthorizationErrorCode;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * In-memory policy application service used by the Summer Sample.  The service
 * deliberately models the same seams as the production design (drafts,
 * immutable published versions, optimistic locking and runtime evaluation),
 * while keeping persistence out of this sample module.
 */
@Service
final class PolicyService {
    private final com.abluepoint.summer.sample.authorization.application.permission.ManagedConsumerAccess managedAccess;
    PolicyService() { this(null); }
    @org.springframework.beans.factory.annotation.Autowired
    PolicyService(com.abluepoint.summer.sample.authorization.application.permission.ManagedConsumerAccess managedAccess) { this.managedAccess = managedAccess; }

    private static final String ENTERPRISE = "ent-acme";
    private static final Set<String> CONDITION_WHITELIST = Set.of(
            "organizationId", "risk", "riskLevel", "riskLevels", "subject", "actor",
            "initiator", "effectiveSubject", "agentId", "applicationId", "scope");
    private static final List<String> RISK_ORDER = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final Map<String, PolicyRecord> policies = new LinkedHashMap<>();
    private final Map<String, Receipt> receipts = new HashMap<>();
    private final Map<String, List<Map<String, Object>>> audit = new HashMap<>();
    private final Map<String, List<PolicyTestCaseView>> testCases = new HashMap<>();

    synchronized List<PolicyView> list(String enterpriseId, Authentication auth) {
        requireRead(enterpriseId, auth);
        return policies.values().stream().filter(p -> enterpriseId.equals(p.enterpriseId)).map(this::view).toList();
    }

    synchronized PolicyView detail(String enterpriseId, String policyId, Authentication auth) {
        requireRead(enterpriseId, auth);
        return view(policy(enterpriseId, policyId));
    }

    synchronized PolicyDetailView detailBundle(String enterpriseId, String policyId, Authentication auth) {
        requireRead(enterpriseId, auth);
        PolicyRecord record = policy(enterpriseId, policyId);
        return new PolicyDetailView(view(record), List.copyOf(record.versions),
                List.of(), List.copyOf(testCases.getOrDefault(enterpriseId + ":" + policyId, List.of())));
    }

    synchronized List<PolicyVersionView> versions(String enterpriseId, String policyId, Authentication auth) {
        requireRead(enterpriseId, auth);
        return List.copyOf(policy(enterpriseId, policyId).versions);
    }

    synchronized PolicyView create(String enterpriseId, PolicyCreateRequest request, String key, String requestId,
                                    Authentication auth) {
        requireWrite(enterpriseId, auth);
        requireKey(key);
        String fp = fingerprint(request);
        Receipt previous = receipts.get(operation(enterpriseId, key));
        if (previous != null) return replay(previous, fp);
        if (request == null || blank(request.name()) || blank(request.code())) {
            throw SummerException.withMessage(PolicyErrorCode.INVALID, "name and code are required");
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

    synchronized PolicyView updateDraft(String enterpriseId, String policyId, PolicyDraftUpdateRequest request,
                                         String ifMatch, String key, String requestId, Authentication auth) {
        requireWrite(enterpriseId, auth);
        requireKey(key);
        String fp = fingerprint(request, ifMatch);
        Receipt previous = receipts.get(operation(enterpriseId, key));
        if (previous != null) return replay(previous, fp);
        PolicyRecord record = policy(enterpriseId, policyId);
        checkEtag(record, ifMatch);
        if (request == null) throw SummerException.withMessage(PolicyErrorCode.INVALID, "Draft update is required");
        PolicyDefinition definition = request.definition() == null
                ? (record.draft == null ? record.current.definition() : record.draft.definition())
                : normalize(request.definition());
        validateDefinition(definition, false);
        if (request.name() != null) {
            if (blank(request.name())) throw SummerException.withMessage(PolicyErrorCode.INVALID, "name is required");
            record.name = request.name().trim();
        }
        if (request.code() != null) {
            if (blank(request.code())) throw SummerException.withMessage(PolicyErrorCode.INVALID, "code is required");
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

    synchronized PolicyValidationView validate(String enterpriseId, String policyId, String key, String requestId,
                                                Authentication auth) {
        requireWrite(enterpriseId, auth);
        requireKey(key);
        String fp = fingerprint(policyId, "validate");
        Receipt previous = receipts.get(operation(enterpriseId, key));
        if (previous != null) return replay(previous, fp);
        PolicyRecord record = policy(enterpriseId, policyId);
        validateDefinition(record.draft == null ? (record.current == null ? null : record.current.definition())
                : record.draft.definition(), true);
        PolicyValidationView out = new PolicyValidationView(policyId, true, List.of(), List.of());
        remember(enterpriseId, key, fp, out);
        recordAudit(enterpriseId, policyId, "POLICY_VALIDATED", requestId, auth, null, null);
        return out;
    }

    synchronized PolicyView publish(String enterpriseId, String policyId, String ifMatch, String key, String requestId,
                                     Authentication auth) {
        requireWrite(enterpriseId, auth);
        requireKey(key);
        String fp = fingerprint("publish", ifMatch);
        Receipt previous = receipts.get(operation(enterpriseId, key));
        if (previous != null) return replay(previous, fp);
        PolicyRecord record = policy(enterpriseId, policyId);
        checkEtag(record, ifMatch);
        if (record.draft == null) throw SummerException.withMessage(PolicyErrorCode.CONFLICT, "Policy has no draft");
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

    synchronized PolicyView rollback(String enterpriseId, String policyId, PolicyRollbackRequest request, String ifMatch,
                                      String key, String requestId, Authentication auth) {
        requireWrite(enterpriseId, auth);
        requireKey(key);
        String fp = fingerprint(request, ifMatch);
        Receipt previous = receipts.get(operation(enterpriseId, key));
        if (previous != null) return replay(previous, fp);
        PolicyRecord record = policy(enterpriseId, policyId);
        checkEtag(record, ifMatch);
        if (request == null || request.versionNo() == null) throw SummerException.withMessage(PolicyErrorCode.INVALID, "versionNo is required");
        PolicyVersionView target = record.versions.stream().filter(v -> v.versionNo() == request.versionNo()).findFirst()
                .orElseThrow(() -> SummerException.withMessage(PolicyErrorCode.CONFLICT, "Policy version not found"));
        record.current = target;
        record.lifecycleStatus = "PUBLISHED";
        record.version++;
        PolicyView out = view(record);
        remember(enterpriseId, key, fp, out);
        recordAudit(enterpriseId, policyId, "POLICY_ROLLED_BACK", requestId, auth, target.versionNo(), null);
        return out;
    }

    synchronized PolicyView lifecycle(String enterpriseId, String policyId, String state, String ifMatch, String key,
                                       String requestId, Authentication auth) {
        requireWrite(enterpriseId, auth);
        requireKey(key);
        String fp = fingerprint(state, ifMatch);
        Receipt previous = receipts.get(operation(enterpriseId, key));
        if (previous != null) return replay(previous, fp);
        PolicyRecord record = policy(enterpriseId, policyId);
        checkEtag(record, ifMatch);
        if (!Set.of("PAUSED", "REVOKED").contains(state)) throw SummerException.withMessage(PolicyErrorCode.INVALID, "Invalid lifecycle state");
        if (record.current == null) throw SummerException.withMessage(PolicyErrorCode.CONFLICT, "Policy is not published");
        record.lifecycleStatus = state;
        record.version++;
        PolicyView out = view(record);
        remember(enterpriseId, key, fp, out);
        recordAudit(enterpriseId, policyId, "POLICY_" + state, requestId, auth, record.current.versionNo(), null);
        return out;
    }

    synchronized PolicyView copy(String enterpriseId, String policyId, PolicyCreateRequest request, String key,
                                  String requestId, Authentication auth) {
        requireWrite(enterpriseId, auth);
        requireKey(key);
        String fp = fingerprint(policyId, request);
        Receipt previous = receipts.get(operation(enterpriseId, key));
        if (previous != null) return replay(previous, fp);
        PolicyRecord source = policy(enterpriseId, policyId);
        PolicyCreateRequest input = request == null
                ? new PolicyCreateRequest(source.name + " copy", source.code + "-copy", source.description,
                source.policyType, source.current == null ? source.draft.definition() : source.current.definition())
                : request;
        PolicyView out = create(enterpriseId, input, key + ":copy", requestId, auth);
        remember(enterpriseId, key, fp, out);
        return out;
    }

    synchronized List<PolicyTestCaseView> testCases(String enterpriseId, String policyId, Authentication auth) {
        requireRead(enterpriseId, auth);
        policy(enterpriseId, policyId);
        return List.copyOf(testCases.getOrDefault(enterpriseId + ":" + policyId, List.of()));
    }

    synchronized PolicyTestCaseView createTestCase(String enterpriseId, String policyId, PolicyTestCaseCreateRequest request,
                                                    String key, String requestId, Authentication auth) {
        requireWrite(enterpriseId, auth);
        requireKey(key);
        String fp = fingerprint(policyId, request);
        Receipt previous = receipts.get(operation(enterpriseId, key));
        if (previous != null) return replay(previous, fp);
        policy(enterpriseId, policyId);
        if (request == null || blank(request.name()) || blank(request.expectedDecision())) {
            throw SummerException.withMessage(PolicyErrorCode.INVALID, "name and expectedDecision are required");
        }
        PolicyTestCaseView out = new PolicyTestCaseView("policy-test-" + UUID.randomUUID().toString().substring(0, 8),
                policyId, request.name().trim(), request.input() == null ? Map.of() : Map.copyOf(request.input()),
                request.expectedDecision().toUpperCase(Locale.ROOT), null, null);
        testCases.computeIfAbsent(enterpriseId + ":" + policyId, x -> new ArrayList<>()).add(out);
        remember(enterpriseId, key, fp, out);
        recordAudit(enterpriseId, policyId, "POLICY_TEST_CASE_CREATED", requestId, auth, null, null);
        return out;
    }

    synchronized List<PolicyTestCaseView> runTests(String enterpriseId, String policyId, String key, String requestId,
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

    synchronized PolicyEvaluationView evaluate(PolicyEvaluationRequest request, Authentication auth) {
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
                .filter(rule -> matches(rule, request)).sorted(Comparator.comparingInt(this::priority).reversed()).toList();
        List<String> ids = matches.stream().map(this::ruleId).filter(Objects::nonNull).toList();
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

    synchronized List<Map<String, Object>> audit(String enterpriseId, String policyId, Authentication auth) {
        requireRead(enterpriseId, auth);
        policy(enterpriseId, policyId);
        return List.copyOf(audit.getOrDefault(enterpriseId + ":" + policyId, List.of()));
    }

    private PolicyEvaluationView evaluation(String decision, boolean allow, List<String> matched, boolean approval,
                                             Integer version, String reason, PolicyEvaluationRequest request) {
        PolicyEvaluationIdentity identity = request == null ? new PolicyEvaluationIdentity(null, null, null, null)
                : new PolicyEvaluationIdentity(request.initiator(), request.actor(), request.effectiveSubject(), request.enterpriseId());
        return new PolicyEvaluationView(decision, allow, matched, approval, version, reason, identity,
                request == null ? null : request.requestId());
    }

    private boolean matches(PolicyRule rule, PolicyEvaluationRequest request) {
        if (rule == null) return false;
        if (!matchAny(rule.subjects(), request.effectiveSubject(), request.actor(), request.initiator())
                && !matchAny(rule.subject() == null ? List.of() : List.of(rule.subject()), request.effectiveSubject(), request.actor(), request.initiator())
                && !matchAny(rule.principal() == null ? List.of() : List.of(rule.principal()), request.effectiveSubject(), request.actor(), request.initiator())) return false;
        if (!matchPattern(rule.resources(), request.resource()) || !matchPattern(rule.actions(), request.action())) return false;
        if (rule.riskLevels() != null && !rule.riskLevels().isEmpty() && !rule.riskLevels().stream().anyMatch(x -> x.equalsIgnoreCase(request.risk()))) return false;
        if (rule.minRisk() != null && risk(request.risk()) < risk(rule.minRisk())) return false;
        if (rule.maxRisk() != null && risk(request.risk()) > risk(rule.maxRisk())) return false;
        if (rule.riskThreshold() != null && risk(request.risk()) < risk(rule.riskThreshold())) return false;
        if (rule.conditions() != null) {
            for (Map.Entry<String, Object> entry : rule.conditions().entrySet()) {
                String actual = conditionValue(entry.getKey(), request);
                // A condition is only evaluated when that context attribute is
                // present.  The caller may intentionally omit optional context
                // (for example an organization) and the rule remains eligible.
                if (actual != null && !Objects.equals(String.valueOf(entry.getValue()), actual)) return false;
            }
        }
        return true;
    }

    private String conditionValue(String key, PolicyEvaluationRequest request) {
        return switch (key) {
            case "organizationId" -> request.organizationId();
            case "risk", "riskLevel" -> request.risk();
            case "subject", "effectiveSubject" -> request.effectiveSubject();
            case "actor" -> request.actor();
            case "initiator" -> request.initiator();
            case "scope", "agentId", "applicationId" -> null;
            default -> null;
        };
    }

    private boolean matchAny(List<String> values, String... subjects) {
        if (values == null || values.isEmpty()) return true;
        return values.stream().anyMatch(value -> "*".equals(value) || Arrays.stream(subjects).anyMatch(s -> s != null && value.equalsIgnoreCase(s)));
    }

    private boolean matchPattern(List<String> patterns, String value) {
        if (patterns == null || patterns.isEmpty()) return true;
        if (value == null) return false;
        return patterns.stream().anyMatch(pattern -> "*".equals(pattern) ||
                (pattern.endsWith("*") && value.startsWith(pattern.substring(0, pattern.length() - 1))) || pattern.equals(value));
    }

    private int priority(PolicyRule rule) { return rule.priority() == null ? 0 : rule.priority(); }
    private String effect(PolicyRule rule) { return rule.effect() == null ? "DENY" : rule.effect(); }
    private String ruleId(PolicyRule rule) { return rule.id(); }
    private int risk(String value) { if (value == null) return 0; int index = RISK_ORDER.indexOf(value.toUpperCase(Locale.ROOT)); return index < 0 ? 0 : index; }

    private void validateDefinition(PolicyDefinition definition) {
        validateDefinition(definition, true);
    }

    private void validateDefinition(PolicyDefinition definition, boolean strictConditions) {
        if (definition == null) throw SummerException.withMessage(PolicyErrorCode.INVALID, "definition is required");
        if (definition.defaultDecision() != null && !Set.of("ALLOW", "DENY").contains(definition.defaultDecision().toUpperCase(Locale.ROOT))) {
            throw SummerException.withMessage(PolicyErrorCode.INVALID, "defaultDecision must be ALLOW or DENY");
        }
        if (definition.rules() == null) return;
        for (PolicyRule rule : definition.rules()) {
            if (rule == null) throw SummerException.withMessage(PolicyErrorCode.INVALID, "rule is required");
            if (strictConditions && rule.conditions() != null) {
                for (String condition : rule.conditions().keySet()) {
                    if (!CONDITION_WHITELIST.contains(condition)) throw SummerException.withMessage(PolicyErrorCode.INVALID, "Unsupported condition: " + condition);
                }
            }
            if (rule.effect() != null && !Set.of("ALLOW", "DENY").contains(rule.effect().toUpperCase(Locale.ROOT))) {
                throw SummerException.withMessage(PolicyErrorCode.INVALID, "Invalid rule effect");
            }
        }
    }

    private PolicyDefinition normalize(PolicyDefinition input) {
        if (input == null) return new PolicyDefinition("DENY", true, "DENY", List.of());
        return new PolicyDefinition(input.defaultDecision() == null ? "DENY" : input.defaultDecision().toUpperCase(Locale.ROOT),
                input.denyOverrides() == null || input.denyOverrides(), input.defaultEffect() == null ? "DENY" : input.defaultEffect().toUpperCase(Locale.ROOT),
                input.rules() == null ? List.of() : List.copyOf(input.rules()));
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
        if (p == null || !enterpriseId.equals(p.enterpriseId)) throw SummerException.withMessage(PolicyErrorCode.CONFLICT, "Policy not found");
        return p;
    }

    private void requireRead(String enterpriseId, Authentication auth) { require(enterpriseId, auth, "enterprise.policies.read", "enterprise.policies.write"); }
    private void requireWrite(String enterpriseId, Authentication auth) { require(enterpriseId, auth, "enterprise.policies.write"); }

    private void require(String enterpriseId, Authentication auth, String... capabilities) {
        if (ENTERPRISE.equals(enterpriseId) && managedAccess != null
            && managedAccess.require(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise(enterpriseId), auth, capabilities)) return;
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
        if (ifMatch == null || !ifMatch.equals("\"" + record.version + "\"")) throw SummerException.withMessage(SampleTransportErrorCode.PRECONDITION_FAILED, "The resource has changed");
    }

    private static void requireKey(String key) { if (key == null || key.isBlank()) throw SummerException.withMessage(PolicyErrorCode.INVALID, "Idempotency-Key is required"); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String operation(String enterpriseId, String key) { return enterpriseId + ":" + key; }
    private static String principal(Authentication auth) { return auth == null ? null : auth.getName(); }

    private static String fingerprint(Object... values) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Arrays.deepToString(values).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    @SuppressWarnings("unchecked") private <T> T replay(Receipt previous, String fingerprint) {
        if (!previous.fingerprint.equals(fingerprint)) throw SummerException.withMessage(AuthorizationErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency key reused");
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
