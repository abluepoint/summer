package com.abluepoint.summer.sample.plugin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"summer.security.storage.keys=memory", "spring.datasource.url=jdbc:h2:mem:plugin-configuration;MODE=MySQL;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class PluginConfigurationHttpTest {
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    @Autowired org.springframework.jdbc.core.JdbcTemplate pluginDb;
    String pluginEnterpriseRole;
    void grantExactPluginOperations() {
        for (String subject : java.util.List.of("admin"))
            current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), subject,
                    (handler, path, method) -> handler.getBeanType() == PluginController.class
                            && path.startsWith("/api/v1/platform/plugins"));
        for (String subject : java.util.List.of("alice", "bob")) {
            pluginEnterpriseRole = current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"), subject,
                    (handler, path, method) -> handler.getBeanType() == PluginController.class
                            && path.startsWith("/api/v1/enterprises/{enterpriseId}/plugins"));
            current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), subject,
                    (handler, path, method) -> handler.getBeanType().getName().equals("com.abluepoint.summer.sample.customer.CustomerController")
                            && method.equals("GET") && path.equals("/api/customers"));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources interfaceResources;
    com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture managed;
    Boolean originalBobActive;
    String bobMemberId;
    String originalBobMemberStatus;
    final Map<String, String> createdDepartmentAssignments = new java.util.LinkedHashMap<>();
    @Autowired com.abluepoint.summer.sample.organization.OrganizationMembershipProvisioner departments;
    @org.junit.jupiter.api.BeforeEach
    void currentConfigurationFacts() {
        // Install identities before inspecting them; JUnit does not order separate BeforeEach methods.
        grantExactPluginOperations();
        originalBobActive = jdbc.queryForObject("select active from summer_auth_user where username='bob'", Boolean.class);
        bobMemberId = jdbc.queryForObject("select member_id from summer_enterprise_member where enterprise_id='ent-acme' and subject='bob'", String.class);
        originalBobMemberStatus = jdbc.queryForObject("select status from summer_enterprise_member where enterprise_id='ent-acme' and member_id=?", String.class, bobMemberId);
        jdbc.update("update summer_auth_user set active=true where username='bob'");
        jdbc.update("update summer_enterprise_member set status='ACTIVE' where enterprise_id='ent-acme' and member_id=?", bobMemberId);
        assertThat(jdbc.queryForObject("select active from summer_auth_user where username='bob'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select status from summer_enterprise_member where enterprise_id='ent-acme' and member_id=?", String.class, bobMemberId)).isEqualTo("ACTIVE");
        prepareDepartment("alice", "org-engineering");
        prepareDepartment("bob", "org-operations");
        managed = new com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture(jdbc, transactions, interfaceResources);
        managed.configuration("ent-acme", "system-parameters", false, "alice");
        managed.configuration("ent-acme", "dictionaries", false, "alice");
        managed.data("ent-acme", "bob", "system-parameters", "read", "TENANT_ALL");
        managed.data("ent-acme", "bob", "dictionaries", "read", "TENANT_ALL");
        // DraftRevisionFixture reads revisions as operator, independently of the business writer.
        managed.data("ent-acme", "operator", "dictionaries", "read", "TENANT_ALL");
        current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"), "operator",
                (handler, path, method) -> handler.getBeanType().getName().equals("com.abluepoint.summer.sample.dictionary.DictionaryController")
                        && method.equals("GET") && path.equals("/api/dictionaries/{code}/draft"));
    }
    private void prepareDepartment(String subject, String organization) {
        String member = jdbc.queryForObject("select member_id from summer_enterprise_member where enterprise_id='ent-acme' and subject=? and status='ACTIVE'", String.class, subject);
        var existing = jdbc.queryForList("select organization_id from summer_organization_membership where enterprise_id='ent-acme' and member_id=?", String.class, member);
        if (!existing.contains(organization)) {
            departments.assign("ent-acme", member, organization);
            createdDepartmentAssignments.put(member, organization);
        }
        assertThat(jdbc.queryForList("select organization_id from summer_organization_membership where enterprise_id='ent-acme' and member_id=?", String.class, member))
                .containsExactly(organization);
    }
    @org.junit.jupiter.api.AfterEach
    void restoreConfigurationFacts() {
        try {
            if (managed != null) managed.close();
        } finally {
            for (var assignment : createdDepartmentAssignments.entrySet())
                jdbc.update("delete from summer_organization_membership where enterprise_id='ent-acme' and member_id=? and organization_id=?", assignment.getKey(), assignment.getValue());
            if (originalBobMemberStatus != null)
                jdbc.update("update summer_enterprise_member set status=? where enterprise_id='ent-acme' and member_id=?", originalBobMemberStatus, bobMemberId);
            if (originalBobActive != null) jdbc.update("update summer_auth_user set active=? where username='bob'", originalBobActive);
        }
    }
    private static final String SUMMARY="/api/v1/enterprises/ent-acme/plugins/customer-insights/summary";

    @Test void organizationAndSelfPoliciesApplyToConfigurationAndDictionary() throws Exception {
        setup();
        policy("system-parameters","ORG"); policy("dictionaries","ORG");
        mvc.perform(get(SUMMARY).with(actor("alice","org-engineering"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configuration[0].value").value("Visible"))
                .andExpect(jsonPath("$.data.dictionary.items[0].label").value("Published"));
        mvc.perform(get(SUMMARY).with(actor("bob","org-operations"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configuration[0].value").doesNotExist())
                .andExpect(jsonPath("$.data.dictionary.items").isEmpty());
        policy("system-parameters","SELF"); policy("dictionaries","SELF");
        mvc.perform(get(SUMMARY).with(actor("bob","org-engineering"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configuration[0].value").doesNotExist())
                .andExpect(jsonPath("$.data.dictionary.items").isEmpty());
    }
    @Test void publishedSnapshotIgnoresDraftAndRollbackSelectsOldSnapshot() throws Exception {
        setup();
        jdbc.update("update summer_dictionary_item set label='Draft' where dictionary_code='customer-segment'");
        mvc.perform(get(SUMMARY).with(actor("alice","org-engineering"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dictionary.items[0].label").value("Published"));
        publish(1);
        mvc.perform(get(SUMMARY).with(actor("alice","org-engineering"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dictionary.items[0].label").value("Draft"));
        mvc.perform(post("/api/dictionaries/customer-segment/rollback").with(actor("alice","org-engineering"))
                .contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(mvc, "/api/dictionaries/customer-segment/rollback", "{\"expectedVersion\":2,\"targetVersion\":1}")))
                .andExpect(status().isOk());
        mvc.perform(get(SUMMARY).with(actor("alice","org-engineering"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dictionary.items[0].label").value("Published"));
    }
    @Test void privateAndDisabledValuesAreNotPluginConfiguration() throws Exception {
        setup();
        jdbc.update("update summer_system_parameter set status='INACTIVE', version=version+1 where parameter_key='customer-insights.label'");
        mvc.perform(patch("/api/dictionaries/customer-segment").with(actor("alice","org-engineering"))
                .contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(mvc, "/api/dictionaries/customer-segment", "{\"name\":\"Segment\",\"publicReadable\":false}")))
                .andExpect(status().isOk());
        publish(1);
        mvc.perform(get(SUMMARY).with(actor("alice","org-engineering"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configuration[0].value").doesNotExist())
                .andExpect(jsonPath("$.data.dictionary.items").isEmpty());
    }
    @Test void menusNeedTheMenuCapability() throws Exception {
        setup();
        var reader=jwt().jwt(j->j.claim("scope", "enterprise.plugins.read").subject("bob").claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.plugins.read"));
        mvc.perform(get("/api/v1/enterprises/ent-acme/plugins").with(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].menus").isEmpty());
    }
    @Test void effectiveOverrideAndItemScopeAreReusedByPlugin() throws Exception {
        setup();
        mvc.perform(put("/api/system-parameters/customer-insights.label/overrides/org-engineering")
                .with(actor("alice","org-engineering")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"Organization override\",\"publicReadable\":true}")) .andExpect(status().isOk());
        policy("dictionaries","SELF");
        jdbc.update("update summer_dictionary_item set owner_id='bob' where dictionary_code='customer-segment'");
        assertThat(jdbc.queryForObject("select owner_id from summer_dictionary_item where dictionary_code='customer-segment'", String.class)).isEqualTo("bob");
        assertThat(jdbc.queryForObject("select created_by from summer_dictionary_item where dictionary_code='customer-segment'", String.class)).isEqualTo("alice");
        mvc.perform(get(SUMMARY).with(actor("alice","org-engineering"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configuration[0].value").value("Organization override"))
                .andExpect(jsonPath("$.data.dictionary.items").isEmpty());
    }
    @Test void disabledDictionaryAndWithdrawnParameterAreAbsent() throws Exception {
        setup();
        jdbc.update("update summer_system_parameter set public_flag=false,version=version+1 where parameter_key='customer-insights.label'");
        mvc.perform(post("/api/dictionaries/customer-segment/disable").with(actor("alice","org-engineering"))
                .contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(mvc, "/api/dictionaries/customer-segment/disable", "{\"expectedVersion\":1}"))) .andExpect(status().isOk());
        mvc.perform(get(SUMMARY).with(actor("alice","org-engineering"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configuration[0].value").doesNotExist())
                .andExpect(jsonPath("$.data.dictionary.items").isEmpty());
    }
    @Test void unavailableAuthorizationStoreFailsWithoutPluginValues() throws Exception {
        setup();
        mvc.perform(get(SUMMARY).with(actor("alice","org-engineering"))).andExpect(status().isOk());
        jdbc.execute("alter table summer_permission_data_scope rename to unavailable_plugin_policy");
        try {
            mvc.perform(get(SUMMARY).with(actor("alice","org-engineering"))).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value("SUMMER_DEPENDENCY_UNAVAILABLE"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        } finally { jdbc.execute("alter table unavailable_plugin_policy rename to summer_permission_data_scope"); }
    }
    private void setup() throws Exception {
        var platform=jwt().jwt(j->j.claim("scope", "platform.plugins.write").subject("admin").claim("platform_capabilities",List.of("platform.plugins.write")))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.plugins.write"));
        mvc.perform(post("/api/v1/platform/plugins").with(platform).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pluginId\":\"customer-insights\",\"version\":\"1.0.0\",\"artifactDigest\":\"sha256:customer-insights-v1\"}")) .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/platform/plugins/customer-insights:enable").with(platform)).andExpect(status().isOk());
        mvc.perform(post("/api/system-parameters").with(actor("alice","org-engineering")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"customer-insights.label\",\"type\":\"STRING\",\"value\":\"Visible\",\"public\":true}")) .andExpect(status().isCreated());
        mvc.perform(post("/api/dictionaries").with(actor("alice","org-engineering")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"customer-segment\",\"name\":\"Segment\",\"publicReadable\":true}")) .andExpect(status().isOk());
        mvc.perform(post("/api/dictionaries/customer-segment/items").with(actor("alice","org-engineering")).contentType(MediaType.APPLICATION_JSON)
                .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(mvc, "/api/dictionaries/customer-segment/items", "{\"value\":\"standard\",\"label\":\"Published\",\"sortOrder\":1}"))) .andExpect(status().isOk());
        // Dictionary creation has no organization field; provision row ownership as fixture metadata.
        jdbc.update("update summer_system_parameter set organization_id='org-engineering' where parameter_key='customer-insights.label'");
        jdbc.update("update summer_dictionary_type set organization_id='org-engineering' where code='customer-segment'");
        jdbc.update("update summer_dictionary_item set organization_id='org-engineering' where dictionary_code='customer-segment'");
        publish(0);
    }
    private void publish(int expected) throws Exception {
        mvc.perform(post("/api/dictionaries/customer-segment/publish").with(actor("alice","org-engineering"))
                .contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(mvc, "/api/dictionaries/customer-segment/publish", "{\"expectedVersion\":"+expected+"}"))) .andExpect(status().isOk());
    }
    private void policy(String resource,String type) {
        String preset = type.equals("ORG") ? "DEPARTMENT" : "SELF";
        for (String subject : List.of("alice", "bob")) {
            managed.revokeRead("ent-acme", subject, resource);
            managed.data("ent-acme", subject, resource, "read", preset);
        }
    }
    private static RequestPostProcessor actor(String subject,String org) {
        var caps=List.of("system.parameter.read","system.parameter.write","platform.dictionary.read","platform.dictionary.write","enterprise.data-scope.read","enterprise.data-scope.write","enterprise.plugins.read","enterprise.plugins.customer-insights.read","customer.read");
        return jwt().jwt(j->j.claim("scope", caps.stream().map(value -> value.replaceFirst("^SCOPE_", "")).collect(java.util.stream.Collectors.joining(" "))).subject(subject).claim("organization_id",org)
                .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE")))
                .claim("enterprise_capabilities",Map.of("ent-acme",caps)))
                .authorities(caps.stream().map(v->new SimpleGrantedAuthority("SCOPE_"+v)).toArray(SimpleGrantedAuthority[]::new));
    }
}
