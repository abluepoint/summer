package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static com.abluepoint.summer.sample.authorization.DataScopeManagedAuthorizationTestFixture.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Current DATA grants exercise business HTTP; retired expression APIs remain explicit refusals. */
@SpringBootTest(properties = {"summer.security.storage.keys=memory", "summer.sample.cache.enabled=false",
        "summer.sample.resilience.enabled=false", "summer.sample.authorization.managed-bootstrap-domains=ENTERPRISE:ent-acme"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(DataScopeManagedAuthorizationTestFixture.Schema.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DataScopeEnforcementHttpTest {
    private static final String BASE = "/api/v1/enterprises/ent-acme";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ManagedInterfaceResources interfaces;
    @Autowired ObjectMapper json;
    private DataScopeManagedAuthorizationTestFixture fixture;

    private String positiveBobPriorStatus;
    @org.junit.jupiter.api.AfterEach void restorePositiveBobMembership() {
        if (positiveBobPriorStatus != null)
            jdbc.update("update summer_enterprise_member set status=? where enterprise_id='ent-acme' and subject='bob'", positiveBobPriorStatus);
    }

    @BeforeEach
    void setUp() {
        // This scenario explicitly requires an active owner; the shared identity fixture never revives disabled members.
        positiveBobPriorStatus = jdbc.queryForObject("select status from summer_enterprise_member where enterprise_id='ent-acme' and subject='bob'", String.class);
        jdbc.update("update summer_enterprise_member set status='ACTIVE' where enterprise_id='ent-acme' and subject='bob'");
        fixture = new DataScopeManagedAuthorizationTestFixture(jdbc, interfaces);
        fixture.interfaces("summer-user");
        for (String subject : List.of("alice", "bob")) {
            fixture.interfaces(subject);
            for (String resource : List.of("system-parameters", "dictionaries")) {
                for (String action : List.of("read", "create", "update")) {
                    fixture.data(subject, resource, action, "TENANT_ALL");
                }
            }
        }
    }

    @Test
    void absentDataGrantKeepsPreviewListDetailAndWritesOutsideTheRange() throws Exception {
        create("scope.alice", "alice", "org-engineering");
        for (String action : List.of("read", "create", "update")) fixture.revoke("alice", "system-parameters", action);
        mvc.perform(get("/api/system-parameters").with(member("alice"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0)).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/system-parameters/scope.alice").with(member("alice"))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/system-parameters/scope.alice").with(member("alice"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"blocked\",\"version\":0}"))
                .andExpect(status().isNotFound());
        preview("scope.alice", "alice", "read", false);
        mvc.perform(post("/api/system-parameters").with(member("alice")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"scope.denied\",\"type\":\"STRING\",\"value\":\"v\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/system-parameters/scope.alice").with(member("bob")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("v"));
        assertThat(jdbc.queryForObject("select count(*) from summer_system_parameter where parameter_key='scope.denied'", Integer.class)).isZero();
    }

    @Test
    void specifiedDepartmentsHaveIdenticalPreviewPagedListAndDetailResults() throws Exception {
        create("scope.allowed", "alice", "org-engineering");
        create("scope.wrong.org", "alice", "org-operations");
        create("scope.other.owner", "bob", "org-engineering");
        scope("alice", "system-parameters", "read", "SPECIFIED_DEPARTMENTS", "org-engineering");
        mvc.perform(get("/api/system-parameters?size=1").with(member("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].key").value("scope.allowed"));
        mvc.perform(get("/api/system-parameters/scope.wrong.org").with(member("alice"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/system-parameters/scope.allowed").with(member("alice"))).andExpect(status().isOk());
        mvc.perform(get("/api/system-parameters/scope.other.owner").with(member("alice"))).andExpect(status().isOk());
        preview("scope.allowed", "alice", "read", true);
        preview("scope.wrong.org", "alice", "read", false);
    }

    @Test
    void dictionaryWritesCannotBypassAnItemScopeWhenParentRemainsVisible() throws Exception {
        mvc.perform(post("/api/dictionaries").with(member("alice")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"scope-dictionary\",\"name\":\"Scope\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/dictionaries/scope-dictionary/items").with(member("bob")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedDraftRevision\":" + draftRevision("bob")
                        + ",\"value\":\"private\",\"label\":\"Private\",\"sortOrder\":1}"))
                .andExpect(status().isOk());
        scope("alice", "dictionaries", "read", "SELF");
        scope("alice", "dictionaries", "update", "SELF");
        mvc.perform(get("/api/dictionaries").with(member("alice"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'scope-dictionary')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.code == 'scope-dictionary')].items[0]").doesNotExist());
        long revision = draftRevision("alice");
        mvc.perform(patch("/api/dictionaries/scope-dictionary/items/private").with(member("alice"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedDraftRevision\":" + revision
                        + ",\"label\":\"Not allowed\",\"sortOrder\":1}"))
                .andExpect(status().isNotFound());
        assertThat(draftRevision("alice")).isEqualTo(revision);
        mvc.perform(get("/api/dictionaries").with(member("bob")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.code == 'scope-dictionary')].items[0].label").value(org.hamcrest.Matchers.contains("Private")));
    }

    @Test
    void selfAndSpecifiedDepartmentPermissionsUnionWithoutAddingUnmatchedRows() throws Exception {
        create("branch.allowed", "alice", "org-engineering");
        create("branch.denied", "bob", "org-engineering");
        create("branch.other", "bob", "org-operations");
        scope("alice", "system-parameters", "read", "SELF");
        fixture.data("alice", "system-parameters", "read", "SPECIFIED_DEPARTMENTS", "org-operations");
        mvc.perform(get("/api/system-parameters").with(member("alice"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/system-parameters/branch.allowed").with(member("alice"))).andExpect(status().isOk());
        mvc.perform(get("/api/system-parameters/branch.denied").with(member("alice"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/system-parameters/branch.other").with(member("alice"))).andExpect(status().isOk());
    }

    @Test
    void organizationTreeIncludesDescendantsAndKeepsUnrelatedRowsOutsideTheQuery() throws Exception {
        jdbc.update("insert into summer_organization_unit(unit_id,enterprise_id,unit_type,name,code,sort_order,status,created_at,updated_at) values('org-unrelated','ent-acme','COMPANY','Unrelated','UNRELATED',0,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        create("tree.root", "alice", "org-acme");
        create("tree.child", "bob", "org-engineering");
        create("tree.unrelated", "bob", "org-unrelated");
        fixture.assignments("alice", "org-acme");
        scope("alice", "system-parameters", "read", "DEPARTMENT_AND_DESCENDANTS");
        mvc.perform(get("/api/system-parameters").with(member("alice"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/system-parameters/tree.root").with(member("alice"))).andExpect(status().isOk());
        mvc.perform(get("/api/system-parameters/tree.child").with(member("alice"))).andExpect(status().isOk());
        mvc.perform(get("/api/system-parameters/tree.unrelated").with(member("alice"))).andExpect(status().isNotFound());
        preview("tree.child", "alice", "read", true);
    }

    @Test
    void scopeUnionCannotEscapeTheTenantBoundaryAndSubjectValuesAreNeverSql() throws Exception {
        create("scope.boundary", "alice", "org-engineering");
        String injected = "alice' OR 1=1 --";
        fixture.interfaces(injected);
        fixture.data(injected, "system-parameters", "read", "SELF");
        mvc.perform(get("/api/system-parameters").with(member(injected))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/system-parameters/scope.boundary").with(member(injected))).andExpect(status().isNotFound());
        jdbc.update("insert into summer_system_parameter(id,tenant_id,owner_id,created_by,parameter_key,value_type,value_json,required_flag,public_flag,status,version,created_at,updated_at) values('foreign08','ent-other','alice','alice','scope.foreign','STRING','\"foreign\"',false,false,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        scope("alice", "system-parameters", "read", "SELF");
        fixture.data("alice", "system-parameters", "read", "TENANT_ALL");
        mvc.perform(get("/api/system-parameters").with(member("alice"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/system-parameters/scope.foreign").with(member("alice"))).andExpect(status().isNotFound());
    }

    @Test
    void legacyPolicyExpressionsAreRetiredWithoutReplacingCurrentPermissions() throws Exception {
        create("scope.retained", "alice", "org-engineering");
        var before = legacyRows();
        var current = jdbc.queryForList("select * from summer_permission_data_scope order by domain_key,permission_id");
        for (String body : List.of(
                "{\"rules\":[{\"type\":\"TENANT\"}]}",
                "{\"conditions\":{\"operator\":\"AND\",\"children\":[]}}",
                "{\"conditions\":{\"field\":\"CREATOR\",\"operator\":\"MATCHES\",\"value\":\".*\"}}",
                "{\"conditions\":{\"field\":\"CREATOR\",\"operator\":\"IN\",\"value\":[]}}",
                "{\"rules\":[{\"type\":\"TENANT\",\"effect\":\"IGNORE\"}]}")) {
            mvc.perform(put(BASE + "/data-scopes/system-parameters").with(member("summer-user"))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isNotFound());
        }
        assertThat(legacyRows()).isEqualTo(before);
        assertThat(jdbc.queryForList("select * from summer_permission_data_scope order by domain_key,permission_id")).isEqualTo(current);
        mvc.perform(get("/api/system-parameters/scope.retained").with(member("alice"))).andExpect(status().isOk());
    }

    @Test
    void retiredUserDenialAndHypotheticalPreviewCannotSupplyAuthority() throws Exception {
        create("binding.alice", "alice", "org-engineering");
        var before = legacyRows();
        mvc.perform(put(BASE + "/data-scopes/system-parameters/bindings/USER/alice")
                .with(member("summer-user")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rules\":[{\"type\":\"TENANT\"}]}"))
                .andExpect(status().isNotFound());
        assertThat(legacyRows()).isEqualTo(before);
        fixture.revoke("alice", "system-parameters", "read");
        mvc.perform(get("/api/system-parameters").with(member("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/system-parameters/binding.alice").with(member("alice"))).andExpect(status().isNotFound());
        mvc.perform(post(BASE + "/data-resources/system-parameters/preview")
                .with(member("summer-user")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"alice\",\"userDenies\":[],\"roleAllows\":[\"TENANT_ALL\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/data-scopes/system-parameters:preview").with(member("summer-user"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"alice\"}"))
                .andExpect(status().isNotFound());
        preview("binding.alice", "alice", "read", false);
    }

    @Test
    void persistedRoleReadRangeDoesNotGrantUpdateAndRevocationTakesEffect() throws Exception {
        create("roles.alice", "alice", "org-engineering");
        create("roles.bob", "bob", "org-operations");
        scope("alice", "system-parameters", "read", "SPECIFIED_DEPARTMENTS", "org-engineering");
        fixture.revoke("alice", "system-parameters", "update");
        mvc.perform(get("/api/system-parameters").with(member("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(patch("/api/system-parameters/roles.alice").with(member("alice"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"blocked\",\"version\":0}"))
                .andExpect(status().isNotFound());
        preview("roles.alice", "alice", "read", true);
        preview("roles.alice", "alice", "update", false);
        fixture.revoke("alice", "system-parameters", "read");
        mvc.perform(get("/api/system-parameters").with(member("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        preview("roles.alice", "alice", "read", false);
        mvc.perform(get("/api/system-parameters/roles.alice").with(member("bob")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("v"));
    }

    private void scope(String subject, String resource, String action, String preset, String... departments) {
        fixture.revoke(subject, resource, action);
        fixture.data(subject, resource, action, preset, departments);
    }

    private void create(String key, String subject, String organization) throws Exception {
        mvc.perform(post("/api/system-parameters").with(member(subject)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("key", key, "type", "STRING", "value", "v", "organizationId", organization))))
                .andExpect(status().isCreated());
    }

    private void preview(String key, String subject, String action, boolean allowed) throws Exception {
        String id = jdbc.queryForObject("select id from summer_system_parameter where tenant_id='ent-acme' and parameter_key=?", String.class, key);
        mvc.perform(post(BASE + "/data-resources/system-parameters/preview").with(member("summer-user"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("subject", subject, "recordId", id, "action", action))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowed").value(allowed));
    }

    private long draftRevision(String subject) throws Exception {
        var result = mvc.perform(get("/api/dictionaries/scope-dictionary/draft").with(member(subject)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data").path("draftRevision").asLong();
    }

    private Map<String, List<Map<String, Object>>> legacyRows() {
        return Map.of(
                "policies", jdbc.queryForList("select * from summer_data_scope_policy order by policy_id"),
                "bindings", jdbc.queryForList("select * from summer_data_scope_binding order by binding_id"),
                "audit", jdbc.queryForList("select * from summer_data_scope_policy_audit order by audit_id"));
    }
}
