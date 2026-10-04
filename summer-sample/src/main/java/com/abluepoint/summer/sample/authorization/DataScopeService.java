package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.customer.CustomerAuditService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class DataScopeService {
    static final String ENTERPRISE = "ent-acme";
    private static final Set<String> ALLOWED_FIELDS = Set.of("TENANT", "ORGANIZATION", "DEPARTMENT", "ORGANIZATION_TREE", "SELF", "CREATOR", "OWNER");
    private static final Set<String> ALLOWED_LOGICAL_OPERATORS = Set.of("AND", "OR");
    private static final Set<String> ALLOWED_COMPARATORS = Set.of("EQ", "EQUALS", "=", "IN", "NE", "NOT_EQUALS", "!=", "NOT_IN", "CONTAINS", "MATCHES");
    private final DataScopePolicyRepository repository;
    private final DataScopePolicyAuditRepository auditRepository;
    private final ObjectMapper mapper;
    private final CustomerAuditService audit;
    private final AuthorizationChangeEventService changeEvents;

    DataScopeService(DataScopePolicyRepository repository, DataScopePolicyAuditRepository auditRepository,
                     ObjectMapper mapper, CustomerAuditService audit, AuthorizationChangeEventService changeEvents) {
        this.repository = repository;
        this.auditRepository = auditRepository;
        this.mapper = mapper;
        this.audit = audit;
        this.changeEvents = changeEvents;
        repository.findByEnterpriseIdAndResource(ENTERPRISE, "system-parameters")
                .orElseGet(() -> repository.save(new DataScopePolicyEntity(UUID.randomUUID().toString(), ENTERPRISE,
                        "system-parameters", "AND", "[{\"type\":\"TENANT\"}]")));
        repository.findByEnterpriseIdAndResource(ENTERPRISE, "dictionaries")
                .orElseGet(() -> repository.save(new DataScopePolicyEntity(UUID.randomUUID().toString(), ENTERPRISE,
                        "dictionaries", "AND", "[{\"type\":\"TENANT\"}]")));
    }

    @Transactional(readOnly = true)
    List<DataScopePolicyView> list(String enterpriseId, Authentication auth) {
        require(enterpriseId, "enterprise.data-scope.read", auth);
        return repository.findAll().stream().filter(p -> enterpriseId.equals(p.enterpriseId())).map(this::view).toList();
    }

    @Transactional(readOnly = true)
    DataScopePolicyView get(String enterpriseId, String resource, Authentication auth) {
        require(enterpriseId, "enterprise.data-scope.read", auth);
        return repository.findByEnterpriseIdAndResource(enterpriseId, resource).map(this::view)
                .orElseThrow(() -> new NoSuchElementException("Data scope policy was not configured"));
    }

    @Transactional
    DataScopePolicyView put(String enterpriseId, String resource, DataScopePolicyRequest request, Authentication auth) {
        return put(enterpriseId, resource, request, null, auth);
    }

    @Transactional
    DataScopePolicyView put(String enterpriseId, String resource, DataScopePolicyRequest request, String requestId, Authentication auth) {
        require(enterpriseId, "enterprise.data-scope.write", auth);
        if (request == null) throw new IllegalArgumentException("policy request is required");
        if (request.sql() != null && !request.sql().isBlank()) throw new IllegalArgumentException("raw SQL is not accepted");
        String op = upper(request.operator() == null ? "AND" : request.operator());
        if (!ALLOWED_LOGICAL_OPERATORS.contains(op)) throw new IllegalArgumentException("operator must be AND or OR");
        List<DataScopeRule> rules = request.rules() == null ? List.of() : List.copyOf(request.rules());
        if (rules.isEmpty() && request.conditions() == null && request.conditionTree() == null) {
            throw new IllegalArgumentException("rules or conditions are required");
        }
        validateRules(rules);
        Object conditions = request.conditions() != null ? request.conditions() : request.conditionTree();
        validateConditionTree(conditions);
        String rulesJson = write(rules);
        String conditionsJson = write(conditions == null ? null : conditions);
        String checksum = checksum(op, rulesJson, conditionsJson);
        DataScopePolicyEntity entity = repository.findByEnterpriseIdAndResource(enterpriseId, resource)
                .orElseGet(() -> new DataScopePolicyEntity(UUID.randomUUID().toString(), enterpriseId, resource, op, rulesJson, conditionsJson, checksum));
        entity.update(op, rulesJson, conditionsJson, checksum);
        repository.saveAndFlush(entity);
        auditRepository.save(new DataScopePolicyAuditEntity(UUID.randomUUID().toString(), enterpriseId, resource,
                entity.version(), checksum, "DATA_SCOPE_POLICY_UPDATED", principal(auth), requestId));
        audit.recordResourceSuccess("data-scope:" + enterpriseId + ":" + resource, "DATA_SCOPE_POLICY_UPDATED", null, auth);
        changeEvents.enqueue(enterpriseId, "DATA_SCOPE", resource, "DATA_SCOPE_POLICY_UPDATED", entity.version(), requestId);
        return view(entity);
    }

    @Transactional(readOnly = true)
    List<DataScopeAuditView> audit(String enterpriseId, String resource, Authentication auth) {
        require(enterpriseId, "enterprise.data-scope.read", auth);
        return auditRepository.findByEnterpriseIdAndResourceOrderByOccurredAtDesc(enterpriseId, resource).stream()
                .map(e -> new DataScopeAuditView(e.auditId(), e.enterpriseId(), e.resource(), e.version(), e.checksum(),
                        e.action(), e.principal(), e.requestId(), e.occurredAt())).toList();
    }

    @Transactional(readOnly = true)
    DataScopePreviewView preview(String enterpriseId, String resource, DataScopePreviewRequest request, Authentication auth) {
        require(enterpriseId, "enterprise.data-scope.read", auth);
        DataScopePolicyEntity policy = repository.findByEnterpriseIdAndResource(enterpriseId, resource)
                .orElseThrow(() -> new NoSuchElementException("Data scope policy was not configured"));
        DataScopePreviewRequest input = request == null ? new DataScopePreviewRequest(null, null, null, null, List.of(), List.of(), List.of()) : request;
        String subject = blank(input.subject()) ? principal(auth) : input.subject();
        Set<String> userDenies = upperSet(input.userDenies());
        Set<String> roleAllows = upperSet(input.roleAllows());
        List<String> matched = new ArrayList<>();
        List<String> denied = new ArrayList<>();
        for (String token : userDenies) {
            if (matchesScopeToken(token, input, subject) || "*".equals(token) || resource.equalsIgnoreCase(token)) denied.add("USER_DENY:" + token);
        }
        if (!denied.isEmpty()) {
            return previewView(enterpriseId, resource, subject, false, "DENY", "USER_DENY",
                    "An explicit user denial overrides role and policy allows.", matched, denied, policy);
        }
        boolean conditionMatch = policyConditionsMatch(policy.conditionsJson(), input, subject);
        List<DataScopeRule> rules = readRules(policy.rulesJson());
        boolean allow = false;
        boolean deny = false;
        for (DataScopeRule rule : rules) {
            String token = field(rule);
            if (!matchesScopeToken(token, input, subject)) continue;
            if (!roleAllows.isEmpty() && !roleAllows.contains(token) && !"TENANT".equals(token)) continue;
            if (!ruleConditionMatch(rule, input, subject)) continue;
            String effect = upper(blank(rule.effect()) ? "ALLOW" : rule.effect());
            matched.add(token);
            if ("DENY".equals(effect)) deny = true; else allow = true;
        }
        if (conditionMatch) {
            matched.add("CONDITION_TREE");
            allow = true;
        }
        if (deny) {
            return previewView(enterpriseId, resource, subject, false, "DENY", "POLICY_DENY",
                    "A matching policy denial overrides allows.", matched, denied, policy);
        }
        String reason = allow ? "POLICY_ALLOW" : "DEFAULT_DENY";
        return previewView(enterpriseId, resource, subject, allow, allow ? "ALLOW" : "DENY", reason,
                allow ? "The effective scope matched the configured policy." : "No configured scope condition matched.",
                matched, denied, policy);
    }

    private DataScopePreviewView previewView(String enterpriseId, String resource, String subject, boolean allowed,
                                              String decision, String reason, String explanation, List<String> matched,
                                              List<String> denied, DataScopePolicyEntity policy) {
        Map<String, Object> effective = new LinkedHashMap<>();
        effective.put("tenant", enterpriseId);
        effective.put("organizationId", null);
        effective.put("matched", List.copyOf(matched));
        effective.put("denied", List.copyOf(denied));
        return new DataScopePreviewView(enterpriseId, resource, subject, allowed, decision, reason, explanation,
                List.copyOf(matched), List.copyOf(denied), policy.version(), policy.checksum(), effective);
    }

    public ScopeFilter filter(String enterpriseId, String resource, Authentication auth) {
        if (!ENTERPRISE.equals(enterpriseId)) throw new AccessDeniedException("Enterprise context mismatch");
        DataScopePolicyEntity entity = repository.findByEnterpriseIdAndResource(enterpriseId, resource)
                .orElseThrow(() -> new AccessDeniedException("Sensitive resource has no data scope policy"));
        List<DataScopeRule> rules = readRules(entity.rulesJson());
        Set<String> types = rules.stream().map(this::field).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        collectConditionFields(readObject(entity.conditionsJson()), types);
        if (types.isEmpty()) throw new AccessDeniedException("Sensitive resource has no data scope policy");
        String subject = auth == null ? null : auth.getName();
        String org = organizationId(auth);
        return new ScopeFilter(types.contains("TENANT"), types.contains("SELF"), types.contains("CREATOR"), types.contains("OWNER"),
                types.contains("ORGANIZATION") || types.contains("DEPARTMENT") || types.contains("ORGANIZATION_TREE"),
                "AND".equals(entity.operator()), subject, org);
    }

    public void assertWrite(String enterpriseId, String resource, Authentication auth, String organizationId) {
        ScopeFilter f = filter(enterpriseId, resource, auth);
        if (!f.allows(organizationId, auth == null ? null : auth.getName(), auth == null ? null : auth.getName(), auth == null ? null : auth.getName())) {
            throw new AccessDeniedException("Write target is outside the data scope");
        }
    }

    private DataScopePolicyView view(DataScopePolicyEntity p) {
        return new DataScopePolicyView(p.resource(), p.enterpriseId(), p.operator(), readRules(p.rulesJson()), p.version(),
                p.updatedAt(), p.checksum(), readObject(p.conditionsJson()));
    }

    private List<DataScopeRule> readRules(String json) {
        try {
            return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, DataScopeRule.class));
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private Object readObject(String json) {
        if (json == null || "null".equals(json)) return null;
        try { return mapper.readValue(json, Object.class); } catch (RuntimeException e) { return null; }
    }

    private void validateRules(List<DataScopeRule> rules) {
        for (DataScopeRule rule : rules) {
            if (rule == null || field(rule) == null || !ALLOWED_FIELDS.contains(field(rule))) {
                throw new IllegalArgumentException("unsupported data scope field");
            }
            if (rule.effect() != null && !Set.of("ALLOW", "DENY").contains(upper(rule.effect()))) {
                throw new IllegalArgumentException("effect must be ALLOW or DENY");
            }
            validateComparator(rule.operator());
            validateRules(rule.children() == null ? List.of() : rule.children());
        }
    }

    @SuppressWarnings("unchecked")
    private void validateConditionTree(Object node) {
        if (node == null) return;
        if (node instanceof Collection<?> values) { for (Object value : values) validateConditionTree(value); return; }
        if (!(node instanceof Map<?, ?> map)) throw new IllegalArgumentException("conditions must be a structured object");
        Object operator = map.get("operator");
        Object field = map.containsKey("field") ? map.get("field") : map.get("type");
        if (field != null && !ALLOWED_FIELDS.contains(normalizeField(String.valueOf(field)))) throw new IllegalArgumentException("unsupported data scope field");
        if (operator != null) {
            String op = upper(String.valueOf(operator));
            if (!ALLOWED_LOGICAL_OPERATORS.contains(op) && !ALLOWED_COMPARATORS.contains(op)) throw new IllegalArgumentException("unsupported data scope operator");
        }
        Object children = map.get("children");
        if (children == null) children = map.get("rules");
        if (children != null) validateConditionTree(children);
        for (Object key : map.keySet()) {
            if (Set.of("operator", "field", "type", "value", "effect", "children", "rules").contains(String.valueOf(key))) continue;
            throw new IllegalArgumentException("unsupported condition field: " + key);
        }
    }

    private void validateComparator(String operator) {
        if (operator != null && !ALLOWED_COMPARATORS.contains(upper(operator)) && !ALLOWED_LOGICAL_OPERATORS.contains(upper(operator))) {
            throw new IllegalArgumentException("unsupported data scope operator");
        }
    }

    @SuppressWarnings("unchecked")
    private void collectConditionFields(Object node, Set<String> fields) {
        if (node instanceof Collection<?> values) { values.forEach(value -> collectConditionFields(value, fields)); return; }
        if (!(node instanceof Map<?, ?> map)) return;
        Object field = map.get("field") == null ? map.get("type") : map.get("field");
        if (field != null) fields.add(normalizeField(String.valueOf(field)));
        Object children = map.get("children");
        if (children == null) children = map.get("rules");
        collectConditionFields(children, fields);
    }

    private boolean ruleConditionMatch(DataScopeRule rule, DataScopePreviewRequest input, String subject) {
        if (rule.value() == null) return true;
        return compare(rule.operator(), rule.value(), valueFor(field(rule), input, subject));
    }

    @SuppressWarnings("unchecked")
    private boolean policyConditionsMatch(String json, DataScopePreviewRequest input, String subject) {
        Object value = readObject(json);
        if (!(value instanceof Map<?, ?>)) return false;
        return evaluateNode((Map<String, Object>) value, input, subject);
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateNode(Map<String, Object> node, DataScopePreviewRequest input, String subject) {
        String op = upper(String.valueOf(node.getOrDefault("operator", "AND")));
        Object children = node.get("children");
        if (children == null) children = node.get("rules");
        if (children instanceof Collection<?> list && ("AND".equals(op) || "OR".equals(op))) {
            List<Boolean> values = list.stream().filter(Map.class::isInstance).map(Map.class::cast).map(v -> evaluateNode((Map<String, Object>) v, input, subject)).toList();
            return "AND".equals(op) ? !values.isEmpty() && values.stream().allMatch(Boolean.TRUE::equals) : values.stream().anyMatch(Boolean.TRUE::equals);
        }
        String field = node.get("field") == null ? String.valueOf(node.getOrDefault("type", "")) : String.valueOf(node.get("field"));
        return compare(op, node.get("value"), valueFor(normalizeField(field), input, subject));
    }

    private boolean compare(String operator, Object expected, Object actual) {
        String op = upper(blank(operator) ? "EQ" : operator);
        if ("=".equals(op)) op = "EQ";
        if ("!=".equals(op) || "NOT_EQUALS".equals(op)) op = "NE";
        if (actual == null) return false;
        if ("IN".equals(op) || "CONTAINS".equals(op)) return asValues(expected).stream().anyMatch(v -> Objects.equals(String.valueOf(v), String.valueOf(actual)));
        if ("NOT_IN".equals(op)) return asValues(expected).stream().noneMatch(v -> Objects.equals(String.valueOf(v), String.valueOf(actual)));
        boolean equal = Objects.equals(String.valueOf(expected), String.valueOf(actual));
        return "NE".equals(op) ? !equal : equal;
    }

    private Set<String> upperSet(List<String> values) { return values == null ? Set.of() : values.stream().filter(Objects::nonNull).map(DataScopeService::upper).collect(java.util.stream.Collectors.toSet()); }

    private boolean matchesScopeToken(String token, DataScopePreviewRequest input, String subject) {
        return switch (token) {
            case "TENANT" -> true;
            case "SELF", "CREATOR" -> Objects.equals(subject, blank(input.creatorId()) ? subject : input.creatorId());
            case "OWNER" -> Objects.equals(subject, input.ownerId());
            case "ORGANIZATION", "DEPARTMENT", "ORGANIZATION_TREE" -> (input.organizationId() != null && !input.organizationId().isBlank()) || (input.organizationIds() != null && !input.organizationIds().isEmpty());
            default -> false;
        };
    }

    private Object valueFor(String field, DataScopePreviewRequest input, String subject) {
        if (input == null) return subject;
        return switch (upper(field)) {
            case "TENANT" -> ENTERPRISE;
            case "SELF", "CREATOR" -> blank(input.creatorId()) ? subject : input.creatorId();
            case "OWNER" -> input.ownerId();
            case "ORGANIZATION", "DEPARTMENT", "ORGANIZATION_TREE" -> input.organizationId() != null ? input.organizationId() : (input.organizationIds() == null ? null : input.organizationIds());
            default -> null;
        };
    }

    private List<Object> asValues(Object value) {
        if (value instanceof Collection<?> collection) return new ArrayList<>(collection);
        return value == null ? List.of() : List.of(value);
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); } catch (JacksonException e) { throw new IllegalArgumentException("policy cannot be serialized", e); }
    }

    private String checksum(String operator, String rules, String conditions) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((operator + "|" + rules + "|" + conditions).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("checksum unavailable", e); }
    }

    private String field(DataScopeRule rule) { return normalizeField(blank(rule.field()) ? rule.type() : rule.field()); }
    private static String normalizeField(String value) {
        String normalized = upper(value);
        return switch (normalized == null ? "" : normalized) {
            case "ORGANIZATION_ID", "ORG", "ORG_ID" -> "ORGANIZATION";
            case "CREATED_BY", "CREATOR_ID" -> "CREATOR";
            case "OWNER_ID" -> "OWNER";
            default -> normalized;
        };
    }
    private static String upper(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String principal(Authentication auth) { return auth == null ? null : auth.getName(); }

    private void require(String enterpriseId, String capability, Authentication auth) {
        if (!ENTERPRISE.equals(enterpriseId) || auth == null || !hasMembership(auth, enterpriseId) || !hasCapability(auth, enterpriseId, capability)) {
            throw new AccessDeniedException("The principal is not authorized for the enterprise data scope");
        }
    }

    private static boolean hasMembership(Authentication auth, String enterpriseId) {
        if (!(auth instanceof JwtAuthenticationToken jwt)) return false;
        Object claim = jwt.getToken().getClaims().get("enterprise_memberships");
        return claim instanceof Collection<?> c && c.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .anyMatch(m -> enterpriseId.equals(value(m, "enterpriseId", "enterprise_id")) && "ACTIVE".equalsIgnoreCase(value(m, "status", "membership_status")));
    }

    private static boolean hasCapability(Authentication auth, String enterpriseId, String capability) {
        if (!(auth instanceof JwtAuthenticationToken jwt)) return false;
        Object claim = jwt.getToken().getClaims().get("enterprise_capabilities");
        if (!(claim instanceof Map<?, ?> map)) return false;
        Object values = map.get(enterpriseId);
        return values instanceof Collection<?> c && c.stream().map(String::valueOf).anyMatch(capability::equals);
    }

    private static String value(Map<?, ?> map, String first, String second) {
        Object value = map.get(first); if (value == null) value = map.get(second); return value == null ? null : value.toString();
    }

    private static String organizationId(Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwt)) return null;
        var claims = jwt.getToken().getClaims();
        for (String key : List.of("organization_id", "organizationId", "current_organization_id")) { Object value = claims.get(key); if (value != null) return value.toString(); }
        Object context = claims.get("organization_context");
        if (context instanceof Map<?, ?> map && map.get("organizationId") != null) return map.get("organizationId").toString();
        return null;
    }

    public record ScopeFilter(boolean tenant, boolean self, boolean creator, boolean owner, boolean organization,
                              boolean operatorAnd, String subject, String organizationId) {
        public boolean allows(String rowOrg, String rowCreator, String rowOwner, String rowSubject) {
            if (operatorAnd) {
                if (self && !Objects.equals(subject, rowSubject)) return false;
                if (creator && !Objects.equals(subject, rowCreator)) return false;
                if (owner && !Objects.equals(subject, rowOwner)) return false;
                if (organization && !Objects.equals(organizationId, rowOrg)) return false;
                return tenant || self || creator || owner || organization;
            }
            return tenant || (self && Objects.equals(subject, rowSubject)) || (creator && Objects.equals(subject, rowCreator))
                    || (owner && Objects.equals(subject, rowOwner)) || (organization && organizationId != null && Objects.equals(organizationId, rowOrg));
        }
    }
}
