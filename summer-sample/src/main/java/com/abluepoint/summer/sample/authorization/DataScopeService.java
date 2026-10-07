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
import java.util.*;

@Service
public class DataScopeService {
    static final String ENTERPRISE = "ent-acme";
    private final DataScopePolicyRepository repository;
    private final DataScopePolicyAuditRepository auditRepository;
    private final ObjectMapper mapper;
    private final CustomerAuditService audit;
    private final AuthorizationChangeEventService changeEvents;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final DataScopeBindingRepository bindings;
    private final AuthorizationUserBindingRepository users;
    private final AuthorizationRoleRepository roles;

    DataScopeService(DataScopePolicyRepository repository, DataScopePolicyAuditRepository auditRepository,
                     ObjectMapper mapper, CustomerAuditService audit, AuthorizationChangeEventService changeEvents,
                     org.springframework.jdbc.core.JdbcTemplate jdbc, DataScopeBindingRepository bindings,
                     AuthorizationUserBindingRepository users, AuthorizationRoleRepository roles) {
        this.repository = repository;
        this.auditRepository = auditRepository;
        this.mapper = mapper;
        this.audit = audit;
        this.changeEvents = changeEvents;
        this.jdbc = jdbc;
        this.bindings = bindings;
        this.users = users;
        this.roles = roles;
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
        if (!Set.of("AND", "OR").contains(op)) throw new IllegalArgumentException("operator must be AND or OR");
        List<DataScopeRule> rules = request.rules() == null ? List.of() : new ArrayList<>(request.rules());
        if (rules.isEmpty() && request.conditions() == null && request.conditionTree() == null) {
            throw new IllegalArgumentException("rules or conditions are required");
        }
        if (request.conditions() != null && request.conditionTree() != null) throw new IllegalArgumentException("choose conditions or conditionTree, not both");
        Object conditions = request.conditions() != null ? request.conditions() : request.conditionTree();
        String rulesJson = write(rules);
        String conditionsJson = write(conditions == null ? null : conditions);
        compile(enterpriseId, op, rulesJson, conditionsJson, principal(auth), organizationId(auth));
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
        String subjectOrganization = subject.equals(principal(auth)) ? organizationId(auth) : storedOrganization(enterpriseId, subject);
        DataScopeExpression.Compiled policyExpression = compile(enterpriseId, policy.operator(), policy.rulesJson(),
                policy.conditionsJson(), subject, subjectOrganization);
        DataScopeExpression.Row row = new DataScopeExpression.Row(enterpriseId, input.organizationId(),
                blank(input.creatorId()) ? subject : input.creatorId(), input.ownerId(),
                blank(input.creatorId()) ? subject : input.creatorId());
        boolean denied = policyExpression.denied().matches(row);
        boolean allowed = policyExpression.expression().matches(row);
        if ((input.userDenies() != null && !input.userDenies().isEmpty())
                || (input.roleAllows() != null && !input.roleAllows().isEmpty())
                || (input.organizationIds() != null && !input.organizationIds().isEmpty())) {
            throw new IllegalArgumentException("Preview reads persisted bindings; hypothetical authorization inputs are not accepted");
        }
        Effective effectivePolicy = effective(policy, subject, subjectOrganization);
        allowed = effectivePolicy.expression().matches(row);
        boolean userDenied = effectivePolicy.userDenied().matches(row);
        String reason = userDenied ? "USER_DENY" : denied ? "POLICY_DENY" : allowed ? "POLICY_ALLOW" : "DEFAULT_DENY";
        Map<String, Object> effective = new LinkedHashMap<>();
        effective.put("tenant", enterpriseId);
        effective.put("organizationId", subjectOrganization);
        effective.put("bindings", effectivePolicy.sources());
        return new DataScopePreviewView(enterpriseId, resource, subject, allowed, allowed ? "ALLOW" : "DENY", reason,
                "The same validated condition expression is used by database queries and this row preview.",
                allowed ? List.of("POLICY_ALLOW") : List.of(), denied || userDenied ? List.of(reason) : List.of(),
                policy.version(), policy.checksum(), effective);
    }

    public ScopeFilter filter(String enterpriseId, String resource, Authentication auth) {
        if (!ENTERPRISE.equals(enterpriseId)) throw new AccessDeniedException("Enterprise context mismatch");
        if (auth instanceof JwtAuthenticationToken jwt && jwt.getToken().hasClaim("enterprise_memberships") && !hasMembership(auth, enterpriseId))
            throw new AccessDeniedException("Enterprise context mismatch");
        DataScopePolicyEntity policy = repository.findByEnterpriseIdAndResource(enterpriseId, resource)
                .orElseThrow(() -> new AccessDeniedException("Sensitive resource has no data scope policy"));
        try {
            Effective expression = effective(policy, principal(auth), organizationId(auth));
            return new ScopeFilter(enterpriseId, expression.expression(), policy.checksum());
        } catch (IllegalArgumentException invalid) {
            throw new AccessDeniedException("Sensitive resource has an invalid data scope policy");
        }
    }

    @Transactional(readOnly = true)
    List<DataScopeBindingView> bindings(String enterprise, String resource, Authentication auth) {
        require(enterprise, "enterprise.data-scope.read", auth);
        return bindings.findByEnterpriseIdAndResourceOrderBySubjectTypeAscSubjectIdAsc(enterprise, resource)
                .stream().map(this::bindingView).toList();
    }

    @Transactional
    DataScopeBindingView bind(String enterprise, String resource, String kind, String subject,
                              DataScopePolicyRequest request, String requestId, Authentication auth) {
        require(enterprise, "enterprise.data-scope.write", auth);
        if (!Set.of("USER", "ROLE").contains(kind) || blank(subject) || subject.length() > 128)
            throw new IllegalArgumentException("Binding requires USER or ROLE and a subject identifier");
        if (repository.findByEnterpriseIdAndResource(enterprise, resource).isEmpty())
            throw new IllegalArgumentException("Configure the resource policy first");
        if ("ROLE".equals(kind) && roles.findById(subject).filter(r -> enterprise.equals(r.enterpriseId())).isEmpty())
            throw new IllegalArgumentException("Role does not belong to this enterprise");
        if (request == null || request.sql() != null || (request.conditions() != null && request.conditionTree() != null))
            throw new IllegalArgumentException("Invalid binding policy");
        String operator = request.operator() == null ? "AND" : upper(request.operator());
        String rules = write(request.rules() == null ? List.of() : request.rules());
        String conditions = write(request.conditions() == null ? request.conditionTree() : request.conditions());
        compile(enterprise, operator, rules, conditions, subject, storedOrganization(enterprise, subject));
        DataScopeBindingEntity entity = bindings.findByEnterpriseIdAndResourceAndSubjectTypeAndSubjectId(enterprise, resource, kind, subject)
                .orElseGet(DataScopeBindingEntity::new);
        if (entity.id == null) entity.id = UUID.randomUUID().toString();
        entity.enterpriseId = enterprise; entity.resource = resource; entity.subjectType = kind; entity.subjectId = subject;
        entity.operator = operator; entity.rulesJson = rules; entity.conditionsJson = conditions;
        entity.checksum = checksum(operator, rules, conditions); entity.updatedAt = java.time.Instant.now();
        bindings.saveAndFlush(entity);
        auditRepository.save(new DataScopePolicyAuditEntity(UUID.randomUUID().toString(), enterprise, resource,
                entity.version, entity.checksum, "DATA_SCOPE_BINDING_UPDATED", principal(auth), requestId));
        audit.recordResourceSuccess("data-scope:" + enterprise + ":" + resource, "DATA_SCOPE_BINDING_UPDATED", requestId, auth);
        changeEvents.enqueue(enterprise, "DATA_SCOPE", entity.id, "DATA_SCOPE_BINDING_UPDATED", entity.version, requestId);
        return bindingView(entity);
    }

    private DataScopeBindingView bindingView(DataScopeBindingEntity b) {
        return new DataScopeBindingView(b.subjectType, b.subjectId, "USER".equals(b.subjectType) ? "DENY" : "ALLOW",
                b.operator, readRules(b.rulesJson), readObject(b.conditionsJson), b.version, b.checksum);
    }

    private record Effective(DataScopeExpression.Node expression, DataScopeExpression.Node userDenied, List<String> sources) {}
    private Effective effective(DataScopePolicyEntity policy, String subject, String organization) {
        DataScopeExpression.Compiled base = compile(policy.enterpriseId(), policy.operator(), policy.rulesJson(), policy.conditionsJson(), subject, organization);
        List<DataScopeBindingEntity> all = bindings.findByEnterpriseIdAndResourceOrderBySubjectTypeAscSubjectIdAsc(policy.enterpriseId(), policy.resource());
        List<String> roleIds = subject == null ? List.of() : users.findByEnterpriseIdAndUsername(policy.enterpriseId(), subject)
                .map(u -> Arrays.asList(u.roleIds().split("\\n"))).orElse(List.of());
        Set<String> activeRoles = new HashSet<>();
        for (String roleId : roleIds) roles.findById(roleId)
                .filter(r -> policy.enterpriseId().equals(r.enterpriseId()) && "ACTIVE".equals(r.status()))
                .ifPresent(r -> activeRoles.add(roleId));
        List<DataScopeExpression.Node> allows = new ArrayList<>(), denies = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        boolean managedRoles = all.stream().anyMatch(b -> "ROLE".equals(b.subjectType));
        for (DataScopeBindingEntity binding : all) {
            if (("ROLE".equals(binding.subjectType) && activeRoles.contains(binding.subjectId))
                    || ("USER".equals(binding.subjectType) && binding.subjectId.equals(subject))) {
                DataScopeExpression.Node node = compile(policy.enterpriseId(), binding.operator, binding.rulesJson, binding.conditionsJson, subject, organization).expression();
                ("USER".equals(binding.subjectType) ? denies : allows).add(node);
                sources.add(binding.subjectType + ":" + binding.subjectId + ":" + binding.version);
            }
        }
        DataScopeExpression.Node userDenied = new DataScopeExpression.Junction(false, denies);
        DataScopeExpression.Node grant = managedRoles ? new DataScopeExpression.Junction(false, allows) : new DataScopeExpression.Constant(true);
        return new Effective(new DataScopeExpression.Junction(true, List.of(base.expression(), grant, new DataScopeExpression.Not(userDenied))), userDenied, sources);
    }

    private DataScopeExpression.Compiled compile(String enterpriseId, String operator, String rules, String conditions,
                                                 String subject, String organization) {
        return DataScopeExpression.compile(operator, readObject(rules), readObject(conditions),
                new DataScopeExpression.Context(enterpriseId, subject, organization, roots -> descendants(enterpriseId, roots)));
    }

    private List<String> descendants(String enterpriseId, List<String> roots) {
        if (roots.isEmpty()) return List.of();
        // Only organization metadata is expanded in memory. Business rows are always filtered in SQL/JPA.
        List<OrganizationLink> links = jdbc.query("select unit_id, parent_id from summer_organization_unit where enterprise_id = ? and status = 'ACTIVE'",
                (rs, index) -> new OrganizationLink(rs.getString(1), rs.getString(2)), enterpriseId);
        Set<String> result = new TreeSet<>(roots);
        boolean changed;
        do {
            changed = false;
            for (OrganizationLink link : links) if (link.parent() != null && result.contains(link.parent()) && result.add(link.id())) changed = true;
        } while (changed);
        return List.copyOf(result);
    }

    private String storedOrganization(String enterpriseId, String subject) {
        return jdbc.query("select organization_id from summer_organization_context where enterprise_id = ? and subject = ?",
                (rs, index) -> rs.getString(1), enterpriseId, subject).stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

    private record OrganizationLink(String id, String parent) {}

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
        try { return mapper.readValue(json, Object.class); } catch (RuntimeException e) { throw new IllegalArgumentException("policy cannot be decoded", e); }
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); } catch (JacksonException e) { throw new IllegalArgumentException("policy cannot be serialized", e); }
    }

    private String checksum(String operator, String rules, String conditions) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((operator + "|" + rules + "|" + conditions).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("checksum unavailable", e); }
    }

    private static String upper(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String principal(Authentication auth) { return auth == null || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken ? null : auth.getName(); }

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

    public static String organizationId(Authentication auth) {
        if (!(auth instanceof JwtAuthenticationToken jwt)) return null;
        var claims = jwt.getToken().getClaims();
        for (String key : List.of("organization_id", "organizationId", "current_organization_id")) { Object value = claims.get(key); if (value != null) return value.toString(); }
        Object context = claims.get("organization_context");
        if (context instanceof Map<?, ?> map && map.get("organizationId") != null) return map.get("organizationId").toString();
        return null;
    }

    public static final class ScopeFilter {
        private final String enterpriseId;
        private final DataScopeExpression.Node expression;
        private final String fingerprint;
        private ScopeFilter(String enterpriseId, DataScopeExpression.Node expression, String policyChecksum) {
            this.enterpriseId = enterpriseId;
            this.expression = expression;
            try {
                this.fingerprint = policyChecksum + ":" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(expression.toString().getBytes(StandardCharsets.UTF_8)));
            } catch (java.security.NoSuchAlgorithmException failure) {
                throw new IllegalStateException("scope fingerprint unavailable", failure);
            }
        }
        public boolean allows(String rowOrg, String rowCreator, String rowOwner, String rowSubject) {
            return expression.matches(new DataScopeExpression.Row(enterpriseId, rowOrg, rowCreator, rowOwner, rowSubject));
        }
        public <T> org.springframework.data.jpa.domain.Specification<T> specification() {
            return DataScopeExpression.specification(expression);
        }
        public String sqlPredicate() { return DataScopeExpression.sql(expression).predicate(); }
        public Map<String, Object> sqlBindings() { return DataScopeExpression.sql(expression).bindings(); }
        public String fingerprint() { return fingerprint; }
    }
}
