package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.customer.service.CustomerAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataScopeEffectivePolicyTest {
    private static final String ENTERPRISE = "ent-acme";
    private static final String RESOURCE = "system-parameters";
    private static final String TENANT_RULE = "[{\"type\":\"TENANT\"}]";
    private final ObjectMapper mapper = new ObjectMapper();
    private final DataScopePolicyRepository policies = mock(DataScopePolicyRepository.class);
    private final DataScopeBindingRepository bindings = mock(DataScopeBindingRepository.class);
    private final AuthorizationUserBindingRepository users = mock(AuthorizationUserBindingRepository.class);
    private final AuthorizationRoleRepository roles = mock(AuthorizationRoleRepository.class);
    private final DataScopePolicyEntity policy = new DataScopePolicyEntity("policy", ENTERPRISE, RESOURCE, "AND", TENANT_RULE);
    private DataScopeService service;

    @BeforeEach
    void setUp() {
        when(policies.findByEnterpriseIdAndResource(anyString(), anyString())).thenReturn(Optional.of(policy));
        when(bindings.findByEnterpriseIdAndResourceOrderBySubjectTypeAscSubjectIdAsc(ENTERPRISE, RESOURCE)).thenReturn(List.of());
        service = new DataScopeService(policies, mock(DataScopePolicyAuditRepository.class), mapper,
                mock(CustomerAuditService.class), mock(AuthorizationChangeEventService.class), mock(JdbcTemplate.class),
                bindings, users, roles);
    }

    @Test
    void jsonRoleAssignmentsContributeTheUnionOfTheirPersistedRanges() {
        assignRoles(mapper.writeValueAsString(List.of("role-a", "role-b")));
        role("role-a", ENTERPRISE, "ACTIVE");
        role("role-b", ENTERPRISE, "ACTIVE");
        configureBindings(binding("ROLE", "role-a", creator("alice")), binding("ROLE", "role-b", creator("bob")));

        var filter = service.filter(ENTERPRISE, RESOURCE, principal("alice"));
        assertThat(filter.allows(null, "alice", null, null)).isTrue();
        assertThat(filter.allows(null, "bob", null, null)).isTrue();
        assertThat(filter.allows(null, "charlie", null, null)).isFalse();
    }

    @Test
    void legacyNewlineRoleAssignmentsRetainTheirExistingMeaning() {
        assignRoles("role-a\nrole-b");
        role("role-a", ENTERPRISE, "ACTIVE");
        role("role-b", ENTERPRISE, "ACTIVE");
        configureBindings(binding("ROLE", "role-a", creator("alice")), binding("ROLE", "role-b", creator("bob")));

        var filter = service.filter(ENTERPRISE, RESOURCE, principal("alice"));
        assertThat(filter.allows(null, "alice", null, null)).isTrue();
        assertThat(filter.allows(null, "bob", null, null)).isTrue();
    }

    @Test
    void explicitUserDenyIsSubtractedAfterTheRoleUnion() {
        assignRoles(mapper.writeValueAsString(List.of("role-a", "role-b")));
        role("role-a", ENTERPRISE, "ACTIVE");
        role("role-b", ENTERPRISE, "ACTIVE");
        configureBindings(binding("ROLE", "role-a", creator("alice")), binding("ROLE", "role-b", creator("bob")),
                binding("USER", "alice", creator("bob")));

        var filter = service.filter(ENTERPRISE, RESOURCE, principal("alice"));
        assertThat(filter.allows(null, "alice", null, null)).isTrue();
        assertThat(filter.allows(null, "bob", null, null)).isFalse();
        assertThat(preview("bob", null).reasonCode()).isEqualTo("USER_DENY");
    }

    @Test
    void inactiveForeignAndUnassignedRolesNeverSupplyARange() {
        assignRoles(mapper.writeValueAsString(List.of("inactive", "foreign")));
        role("inactive", ENTERPRISE, "DISABLED");
        role("foreign", "ent-other", "ACTIVE");
        role("unassigned", ENTERPRISE, "ACTIVE");
        configureBindings(binding("ROLE", "inactive", TENANT_RULE), binding("ROLE", "foreign", TENANT_RULE),
                binding("ROLE", "unassigned", TENANT_RULE));

        assertThat(service.filter(ENTERPRISE, RESOURCE, principal("alice")).allows(null, "alice", null, null)).isFalse();
        assertThat(service.filter(ENTERPRISE, RESOURCE, principal("unassigned-user")).allows(null, "alice", null, null)).isFalse();
    }

    @Test
    void malformedJsonRoleAssignmentsFailClosedWithoutGrantingValidPrefixes() {
        role("role-a", ENTERPRISE, "ACTIVE");
        configureBindings(binding("ROLE", "role-a", TENANT_RULE));
        for (String invalid : List.of("[\"role-a\",1]", "[\"role-a\",null]", "[\"role-a\",{}]", "[\"role-a\"")) {
            assignRoles(invalid);
            assertThatThrownBy(() -> service.filter(ENTERPRISE, RESOURCE, principal("alice")))
                    .as("invalid persisted role_ids must reject the whole decision")
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void resourceDenyIsEnforcedInBothFilterAndPreviewUnderAndAndOr() {
        String rules = "[{\"type\":\"TENANT\"},{\"field\":\"CREATOR\",\"value\":\"bob\",\"effect\":\"DENY\"}]";
        for (String operator : List.of("AND", "OR")) {
            policy.update(operator, rules, "null", "checksum");
            var filter = service.filter(ENTERPRISE, RESOURCE, principal("alice"));
            assertThat(filter.allows(null, "bob", null, null)).as(operator).isFalse();
            assertThat(filter.allows(null, "alice", null, null)).as(operator).isTrue();
            var preview = preview("bob", null);
            assertThat(preview.allowed()).isFalse();
            assertThat(preview.reasonCode()).isEqualTo("POLICY_DENY");
        }
    }

    @Test
    void nestedAndDenyKeepsItsBranchGateWhenAnotherOrBranchAllowsTheRow() {
        policy.update("OR", "[]", """
                {"operator":"OR","children":[
                  {"operator":"AND","children":[
                    {"field":"ORGANIZATION","value":"org-engineering"},
                    {"field":"CREATOR","value":"bob","effect":"DENY"}
                  ]},
                  {"field":"TENANT"}
                ]}
                """, "checksum");
        var filter = service.filter(ENTERPRISE, RESOURCE, principal("alice"));
        assertThat(filter.allows("org-engineering", "bob", null, null)).isFalse();
        assertThat(filter.allows("org-marketing", "bob", null, null)).isTrue();
        assertThat(filter.allows("org-engineering", "alice", null, null)).isTrue();
    }

    @Test
    void roleUnionCannotOverrideResourceDenialsOrTheResourceMaximum() {
        assignRoles("[\"role-a\"]");
        role("role-a", ENTERPRISE, "ACTIVE");
        configureBindings(binding("ROLE", "role-a", TENANT_RULE));
        policy.update("AND", "[{\"field\":\"ORGANIZATION\",\"value\":\"org-engineering\"},"
                + "{\"field\":\"CREATOR\",\"value\":\"bob\",\"effect\":\"DENY\"}]", "null", "checksum");

        var filter = service.filter(ENTERPRISE, RESOURCE, principal("alice"));
        assertThat(filter.allows("org-engineering", "alice", null, null)).isTrue();
        assertThat(filter.allows("org-engineering", "bob", null, null)).isFalse();
        assertThat(filter.allows("org-marketing", "alice", null, null)).isFalse();
    }

    @Test
    void denyInsideRoleBindingRemainsEffective() {
        assignRoles("[\"role-a\"]");
        role("role-a", ENTERPRISE, "ACTIVE");
        configureBindings(binding("ROLE", "role-a", "[{\"type\":\"TENANT\"},"
                + "{\"field\":\"CREATOR\",\"value\":\"bob\",\"effect\":\"DENY\"}]"));

        var filter = service.filter(ENTERPRISE, RESOURCE, principal("alice"));
        assertThat(filter.allows(null, "alice", null, null)).isTrue();
        assertThat(filter.allows(null, "bob", null, null)).isFalse();
    }

    @Test
    void orBranchesAndDenyNeverExpandTheTenantBoundary() {
        Object rules = mapper.readValue("[{\"field\":\"TENANT\",\"value\":\"ent-other\"},"
                + "{\"field\":\"CREATOR\",\"value\":\"alice\"},"
                + "{\"field\":\"CREATOR\",\"value\":\"bob\",\"effect\":\"DENY\"}]", Object.class);
        var compiled = DataScopeExpression.compile("OR", rules, null,
                new DataScopeExpression.Context(ENTERPRISE, "alice", null, List::copyOf));
        assertThat(compiled.expression().matches(new DataScopeExpression.Row("ent-other", null, "alice", null, null))).isFalse();
        assertThat(compiled.expression().matches(new DataScopeExpression.Row(ENTERPRISE, null, "alice", null, null))).isTrue();
        assertThat(compiled.expression().matches(new DataScopeExpression.Row(ENTERPRISE, null, "bob", null, null))).isFalse();
    }

    private void assignRoles(String stored) {
        when(users.findByEnterpriseIdAndUsername(ENTERPRISE, "alice")).thenReturn(Optional.of(
                new AuthorizationUserBindingEntity("assignment", ENTERPRISE, "alice", stored, "[]", 1)));
    }

    private void role(String id, String enterprise, String status) {
        when(roles.findById(id)).thenReturn(Optional.of(new AuthorizationRoleEntity(id, enterprise, id, id,
                false, status, "", "", null, 1)));
    }

    private void configureBindings(DataScopeBindingEntity... rows) {
        when(bindings.findByEnterpriseIdAndResourceOrderBySubjectTypeAscSubjectIdAsc(ENTERPRISE, RESOURCE)).thenReturn(List.of(rows));
    }

    private static DataScopeBindingEntity binding(String type, String subject, String rules) {
        DataScopeBindingEntity binding = new DataScopeBindingEntity();
        binding.subjectType = type;
        binding.subjectId = subject;
        binding.operator = "OR";
        binding.rulesJson = rules;
        binding.conditionsJson = "null";
        return binding;
    }

    private static String creator(String subject) {
        return "[{\"field\":\"CREATOR\",\"value\":\"" + subject + "\"}]";
    }

    private DataScopePreviewView preview(String creator, String organization) {
        return service.preview(ENTERPRISE, RESOURCE, new DataScopePreviewRequest("alice", organization, creator,
                null, List.of(), List.of()), principal("alice"));
    }

    private static JwtAuthenticationToken principal(String subject) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(subject)
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", ENTERPRISE, "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of(ENTERPRISE, List.of("enterprise.data-scope.read"))).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_enterprise.data-scope.read")));
    }
}
