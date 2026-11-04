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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"summer.security.storage.keys=memory", "spring.datasource.url=jdbc:h2:mem:plugin-configuration;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PluginConfigurationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
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
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":2,\"targetVersion\":1}"))
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
        var reader=jwt().jwt(j->j.subject("bob").claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE"))))
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
        jdbc.update("update summer_dictionary_item set created_by='bob' where dictionary_code='customer-segment'");
        mvc.perform(get(SUMMARY).with(actor("alice","org-engineering"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configuration[0].value").value("Organization override"))
                .andExpect(jsonPath("$.data.dictionary.items").isEmpty());
    }
    @Test void disabledDictionaryAndWithdrawnParameterAreAbsent() throws Exception {
        setup();
        jdbc.update("update summer_system_parameter set public_flag=false,version=version+1 where parameter_key='customer-insights.label'");
        mvc.perform(post("/api/dictionaries/customer-segment/disable").with(actor("alice","org-engineering"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}")) .andExpect(status().isOk());
        mvc.perform(get(SUMMARY).with(actor("alice","org-engineering"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configuration[0].value").doesNotExist())
                .andExpect(jsonPath("$.data.dictionary.items").isEmpty());
    }
    @Test void unavailableAuthorizationStoreFailsWithoutPluginValues() throws Exception {
        setup();
        mvc.perform(get(SUMMARY).with(actor("alice","org-engineering"))).andExpect(status().isOk());
        jdbc.execute("alter table summer_data_scope_policy rename to unavailable_plugin_policy");
        try {
            mvc.perform(get(SUMMARY).with(actor("alice","org-engineering"))).andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error.code").value("SUMMER_INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        } finally { jdbc.execute("alter table unavailable_plugin_policy rename to summer_data_scope_policy"); }
    }
    private void setup() throws Exception {
        var platform=jwt().jwt(j->j.subject("admin").claim("platform_capabilities",List.of("platform.plugins.write")))
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
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":"+expected+"}")) .andExpect(status().isOk());
    }
    private void policy(String resource,String type) throws Exception {
        mvc.perform(put("/api/v1/enterprises/ent-acme/data-scopes/"+resource).with(actor("alice","org-engineering"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"rules\":[{\"type\":\""+type+"\"}]}")) .andExpect(status().isOk());
    }
    private static RequestPostProcessor actor(String subject,String org) {
        var caps=List.of("system.parameter.read","system.parameter.write","platform.dictionary.read","platform.dictionary.write","enterprise.data-scope.read","enterprise.data-scope.write","enterprise.plugins.read","enterprise.plugins.customer-insights.read","customer.read");
        return jwt().jwt(j->j.subject(subject).claim("organization_id",org)
                .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE")))
                .claim("enterprise_capabilities",Map.of("ent-acme",caps)))
                .authorities(caps.stream().map(v->new SimpleGrantedAuthority("SCOPE_"+v)).toArray(SimpleGrantedAuthority[]::new));
    }
}
