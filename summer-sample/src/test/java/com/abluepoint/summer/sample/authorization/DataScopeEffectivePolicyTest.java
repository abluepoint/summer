package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.application.data.DataReadService;
import com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static com.abluepoint.summer.sample.authorization.DataScopeManagedAuthorizationTestFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Persisted current DATA grants; historical expression semantics are tested only as migration input. */
@SpringBootTest(properties = {"summer.security.storage.keys=memory", "summer.sample.cache.enabled=false",
        "summer.sample.resilience.enabled=false", "summer.sample.authorization.managed-bootstrap-domains=ENTERPRISE:ent-acme"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(DataScopeManagedAuthorizationTestFixture.Schema.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DataScopeEffectivePolicyTest {
    private static final String RESOURCE = "system-parameters";
    @Autowired JdbcTemplate db;
    @Autowired ManagedInterfaceResources interfaces;
    @Autowired BusinessDataScopes scopes;
    @Autowired DataReadService reads;
    @Autowired DataScopeService legacy;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper mapper;
    private DataScopeManagedAuthorizationTestFixture fixture;

    private String positiveBobPriorStatus;
    @org.junit.jupiter.api.AfterEach void restorePositiveBobMembership() {
        if (positiveBobPriorStatus != null)
            db.update("update summer_enterprise_member set status=? where enterprise_id='ent-acme' and subject='bob'", positiveBobPriorStatus);
    }

    @BeforeEach
    void setUp() {
        // This scenario explicitly requires an active owner; the shared identity fixture never revives disabled members.
        positiveBobPriorStatus = db.queryForObject("select status from summer_enterprise_member where enterprise_id='ent-acme' and subject='bob'", String.class);
        db.update("update summer_enterprise_member set status='ACTIVE' where enterprise_id='ent-acme' and subject='bob'");
        fixture = new DataScopeManagedAuthorizationTestFixture(db, interfaces);
        fixture.identity("alice");
        fixture.identity("bob");
    }

    @Test
    void normalizedRoleAssignmentsContributeTheUnionOfTheirPersistedRanges() {
        fixture.role("alice", "role08-self");
        fixture.role("alice", "role08-department");
        fixture.dataForRole("role08-self", RESOURCE, "read", "SELF");
        fixture.dataForRole("role08-department", RESOURCE, "read", "SPECIFIED_DEPARTMENTS", "org-operations");
        assertThat(allows("alice", "org-engineering", "alice")).isTrue();
        assertThat(allows("alice", "org-operations", "bob")).isTrue();
        assertThat(allows("alice", "org-engineering", "charlie")).isFalse();
    }

    @Test
    void legacyNewlineRoleTextCannotSupplyOrRemoveCurrentNormalizedGrants() {
        oldAssignment("role08-self\nrole08-department");
        assertThat(allows("alice", "org-engineering", "alice")).isFalse();
        fixture.role("alice", "role08-self");
        fixture.role("alice", "role08-department");
        fixture.dataForRole("role08-self", RESOURCE, "read", "SELF");
        fixture.dataForRole("role08-department", RESOURCE, "read", "SPECIFIED_DEPARTMENTS", "org-operations");
        assertThat(allows("alice", "org-engineering", "alice")).isTrue();
        assertThat(allows("alice", "org-operations", "bob")).isTrue();
    }

    @Test
    void removingOneRoleGrantNarrowsTheUnionWithoutAUserDenyFallback() {
        fixture.data("alice", RESOURCE, "read", "SELF");
        fixture.role("alice", "role08-department");
        String permission = fixture.dataForRole("role08-department", RESOURCE, "read", "SPECIFIED_DEPARTMENTS", "org-operations");
        assertThat(allows("alice", "org-operations", "bob")).isTrue();
        fixture.revoke("role08-department", permission);
        assertThat(allows("alice", "org-engineering", "alice")).isTrue();
        assertThat(allows("alice", "org-operations", "bob")).isFalse();
    }

    @Test
    void inactiveForeignAndUnassignedRolesNeverSupplyARange() {
        fixture.data("alice", RESOURCE, "read", "SELF");
        assertThat(allows("alice", "org-engineering", "alice")).isTrue();
        db.update("update summer_authorization_role set status='DISABLED' where role_id=?", roleId("alice"));
        fixture.advance();
        assertThat(allows("alice", "org-engineering", "alice")).isFalse();
        fixture.data("bob", RESOURCE, "read", "TENANT_ALL");
        assertThat(allows("bob", "org-engineering", "alice")).isTrue();
        assertThat(allows("alice", "org-engineering", "alice")).isFalse();
        // A complete normalized grant in another domain must not contribute to this one.
        foreignGrant();
        assertThat(reads.<Boolean>read(Domain.enterprise("ent-other"), RESOURCE, "read", principal("alice"), DataReadService.Scope::all)).isTrue();
        assertThat(allows("alice", "org-engineering", "alice")).isFalse();
    }

    @Test
    void malformedLegacyRoleJsonCannotGrantPrefixesOrInvalidateCurrentGrants() {
        for (String invalid : List.of("[\"role08-self\",1]", "[\"role08-self\",null]", "[\"role08-self\",{}]", "[\"role08-self\"")) {
            oldAssignment(invalid);
            assertThat(allows("alice", "org-engineering", "alice")).as("retired role text cannot supply authority").isFalse();
        }
        fixture.data("alice", RESOURCE, "read", "SELF");
        assertThat(allows("alice", "org-engineering", "alice")).isTrue();
        assertThat(allows("alice", "org-engineering", "bob")).isFalse();
    }

    @Test
    void historicalResourceDenyIsPreservedUnderAndAndOrForMigration() {
        String rules = "[{\"type\":\"TENANT\"},{\"field\":\"CREATOR\",\"value\":\"bob\",\"effect\":\"DENY\"}]";
        for (String operator : List.of("AND", "OR")) {
            var expression = compile(operator, rules, "null");
            assertThat(expression.matches(row("org-engineering", "bob"))).as(operator).isFalse();
            assertThat(expression.matches(row("org-engineering", "alice"))).as(operator).isTrue();
        }
    }

    @Test
    void historicalNestedAndDenyKeepsItsBranchGateUnderOrForMigration() {
        var expression = compile("OR", "[]", """
                {"operator":"OR","children":[
                  {"operator":"AND","children":[
                    {"field":"ORGANIZATION","value":"org-engineering"},
                    {"field":"CREATOR","value":"bob","effect":"DENY"}]},
                  {"field":"TENANT"}]}
                """);
        assertThat(expression.matches(row("org-engineering", "bob"))).isFalse();
        assertThat(expression.matches(row("org-marketing", "bob"))).isTrue();
        assertThat(expression.matches(row("org-engineering", "alice"))).isTrue();
    }

    @Test
    void roleUnionCannotOverrideTenantOrRegisteredBusinessMaximum() {
        fixture.data("alice", "applications", "read", "SELF");
        fixture.data("alice", "applications", "read", "TENANT_ALL");
        application("visible", DOMAIN, TENANT, "bob", true);
        application("hidden", DOMAIN, TENANT, "alice", false);
        application("foreign", "ENTERPRISE:ent-other", "ent-other", "alice", true);
        application("wrong-domain", "PLATFORM:ent-acme", TENANT, "alice", true);
        var query = new NamedParameterJdbcTemplate(db);
        var ids = reads.read(Domain.enterprise(TENANT), "applications", "read", principal("alice"), range ->
                query.queryForList("select record_id from summer_application_record where " + range.predicate(), range.bindings(), String.class));
        assertThat(ids).containsExactly("visible");
    }

    @Test
    void historicalDenyInsideRoleExpressionRemainsEffectiveForMigration() {
        var expression = compile("OR", "[{\"type\":\"TENANT\"},"
                + "{\"field\":\"CREATOR\",\"value\":\"bob\",\"effect\":\"DENY\"}]", "null");
        assertThat(expression.matches(row(null, "alice"))).isTrue();
        assertThat(expression.matches(row(null, "bob"))).isFalse();
    }

    @Test
    void orBranchesAndDenyNeverExpandTheTenantBoundary() {
        var expression = compile("OR", "[{\"field\":\"TENANT\",\"value\":\"ent-other\"},"
                + "{\"field\":\"CREATOR\",\"value\":\"alice\"},"
                + "{\"field\":\"CREATOR\",\"value\":\"bob\",\"effect\":\"DENY\"}]", "null");
        assertThat(expression.matches(new DataScopeExpression.Row("ent-other", null, "alice", null, null))).isFalse();
        assertThat(expression.matches(row(null, "alice"))).isTrue();
        assertThat(expression.matches(row(null, "bob"))).isFalse();
    }

    @Test
    void historicalNestedComparatorsKeepTheirMeaningAndBindValuesForMigration() {
        var expression = compile("AND", "[]", """
                {"operator":"AND","children":[
                  {"field":"ORGANIZATION","operator":"IN","value":["org-engineering"]},
                  {"operator":"OR","children":[{"field":"CREATOR","operator":"EQ","value":"alice"},
                    {"field":"OWNER","operator":"EQ","value":"carol"}]},
                  {"field":"CREATOR","operator":"NOT_IN","value":["bob"]}]}
                """);
        assertThat(expression.matches(row("org-engineering", "alice"))).isTrue();
        assertThat(expression.matches(row("org-operations", "alice"))).isFalse();
        assertThat(expression.matches(row("org-engineering", "bob"))).isFalse();
        assertThat(expression.matches(new DataScopeExpression.Row(TENANT, "org-engineering", "dave", "carol", null))).isTrue();
        var bound = DataScopeExpression.sql(compile("OR", "[{\"field\":\"CREATOR\",\"value\":\"alice' OR 1=1 --\"}]", "null"));
        assertThat(bound.predicate()).doesNotContain("alice' OR 1=1 --");
        assertThat(bound.bindings()).containsValue("alice' OR 1=1 --");
    }

    @Test
    void malformedHistoricalTreesAndComparatorsFailBeforeMigrationEvaluation() {
        for (String invalid : List.of(
                "{\"operator\":\"AND\",\"children\":[]}",
                "{\"field\":\"CREATOR\",\"operator\":\"MATCHES\",\"value\":\".*\"}",
                "{\"field\":\"CREATOR\",\"operator\":\"IN\",\"value\":[]}",
                "{\"type\":\"TENANT\",\"effect\":\"IGNORE\"}")) {
            assertThatThrownBy(() -> compile("AND", "[]", invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void currentReadSeesRevocationOutsideAnEarlierRepeatableReadSnapshot() {
        String permission = fixture.data("alice", RESOURCE, "read", "SELF");
        var actor = principal("alice");
        assertThat(scopes.<Boolean>read(TENANT, RESOURCE, "read", actor, range -> range.allows(null, "alice", "alice", null))).isTrue();
        var outer = new TransactionTemplate(transactions);
        outer.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var fresh = new TransactionTemplate(transactions);
        fresh.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outer.executeWithoutResult(tx -> {
            assertThat(db.queryForObject("select count(*) from summer_role_permission where domain_key=? and permission_id=?", Integer.class, DOMAIN, permission)).isEqualTo(1);
            fresh.executeWithoutResult(change -> fixture.revoke(roleId("alice"), permission));
            assertThat(db.queryForObject("select count(*) from summer_role_permission where domain_key=? and permission_id=?", Integer.class, DOMAIN, permission)).isEqualTo(1);
            assertThat(scopes.<Boolean>read(TENANT, RESOURCE, "read", actor, range -> range.allows(null, "alice", "alice", null))).isFalse();
        });
        db.update("delete from summer_authorization_domain where domain_key=?", DOMAIN);
        assertThatThrownBy(() -> scopes.read(TENANT, RESOURCE, "read", actor, range -> true)).isInstanceOf(AccessDeniedException.class);
        assertThat(db.queryForObject("select count(*) from summer_authorization_domain where domain_key=?", Integer.class, DOMAIN)).isZero();
    }

    @Test
    void retiredRangeEntryPointRejectsAndConstructionNeverSeedsLegacyPolicies() {
        assertThatThrownBy(() -> legacy.filter(TENANT, RESOURCE, principal("alice")))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("retired");
        new DataScopeService(mock(JdbcTemplate.class));
        assertThat(db.queryForObject("select count(*) from summer_data_scope_policy", Integer.class)).isZero();
    }

    private boolean allows(String subject, String organization, String owner) {
        return scopes.read(TENANT, RESOURCE, "read", principal(subject), range -> range.allows(organization, owner, owner, null));
    }

    private void oldAssignment(String stored) {
        if (db.update("update summer_authorization_user_binding set role_ids=? where enterprise_id=? and username='alice'", stored, TENANT) == 0) {
            db.update("insert into summer_authorization_user_binding(binding_id,enterprise_id,username,role_ids,denied_capability_codes,version,created_at,updated_at) values('fixture08-old',?,'alice',?,'[]',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", TENANT, stored);
        }
    }

    private DataScopeExpression.Node compile(String operator, String rules, String conditions) {
        return DataScopeExpression.compile(operator, mapper.readValue(rules, Object.class), mapper.readValue(conditions, Object.class),
                new DataScopeExpression.Context(TENANT, "alice", null, List::copyOf)).expression();
    }

    private static DataScopeExpression.Row row(String organization, String creator) {
        return new DataScopeExpression.Row(TENANT, organization, creator, creator, null);
    }

    private void application(String id, String domain, String tenant, String owner, boolean visible) {
        db.update("insert into summer_application_record(record_id,domain_key,tenant_id,applicant_id,created_by,business_visible) values(?,?,?,?,?,?)", id, domain, tenant, owner, "proxy", visible);
    }

    private void foreignGrant() {
        String foreign = "ENTERPRISE:ent-other";
        db.update("insert into summer_enterprise(enterprise_id,name,code,status,created_at,updated_at) values('ent-other','Other','OTHER','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        db.update("insert into summer_enterprise_member(enterprise_id,member_id,subject,display_name,email,status,version,created_at,updated_at) values('ent-other','foreign08','alice','Alice','foreign08@example.test','ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        db.update("insert into summer_authorization_domain(domain_key,engine_mode,revision) values(?,'MANAGED',0)", foreign);
        db.update("insert into summer_permission_group(domain_key,group_id,name,sort_order,version) values(?,'foreign08','Foreign',0,0)", foreign);
        db.update("insert into summer_authorization_role(role_id,enterprise_id,name,code,built_in,status,version,created_at,updated_at) values('role08-foreign','ent-other','Foreign','foreign08',false,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        db.update("insert into summer_permission_role(domain_key,role_id) values(?,'role08-foreign')", foreign);
        db.update("insert into summer_permission_user(domain_key,username,version) values(?,'alice',0)", foreign);
        db.update("insert into summer_permission_user_role(domain_key,username,role_id) values(?,'alice','role08-foreign')", foreign);
        db.update("insert into summer_permission_identity(domain_key,permission_id,code,type) values(?,'foreign08','foreign08','DATA')", foreign);
        db.update("insert into summer_permission(domain_key,permission_id,code,name,type,access_mode,group_id,status,version) values(?,'foreign08','foreign08','Foreign','DATA','AUTHORIZED','foreign08','ACTIVE',0)", foreign);
        db.update("insert into summer_permission_data_scope(domain_key,permission_id,data_resource_key,action,scope_preset) values(?,'foreign08','system-parameters','read','TENANT_ALL')", foreign);
        db.update("insert into summer_role_permission(domain_key,role_id,permission_id) values(?,'role08-foreign','foreign08')", foreign);
        fixture.advance();
    }
}
