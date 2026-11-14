package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.cache.VersionedTwoLevelCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.abluepoint.summer.sample.authorization.MultiTenantConfigurationHttpTest.member;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:multi-config-fallback;MODE=MySQL;DB_CLOSE_DELAY=-1","summer.security.storage.keys=memory","summer.sample.resilience.enabled=true","summer.sample.resilience.timeout=5s","summer.sample.resilience.max-attempts=1"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class MultiTenantConfigurationFallbackHttpTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean VersionedTwoLevelCache cache;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources interfaces;
    private BusinessManagedAuthorizationTestFixture managed;
    @org.junit.jupiter.api.BeforeEach void prepareCurrentBusinessFixture() {
        managed = new BusinessManagedAuthorizationTestFixture(jdbc, transactions, interfaces);
    }
    @org.junit.jupiter.api.AfterEach void releaseCurrentBusinessFixture() { managed.close(); }
    @Test void identicalKeysAndSubjectNeverReuseAnotherTenantsFallback() throws Exception {
        for(String tenant:List.of("ent-alpha","ent-beta")) {
            policy(tenant,"system-parameters");
            mvc.perform(post(base(tenant)).with(member("cache-user")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"key\":\"cache.key\",\"type\":\"STRING\",\"value\":\""+tenant+"\",\"public\":true}")) .andExpect(status().isCreated());
            mvc.perform(get(base(tenant)+"/cache.key").with(member("cache-user"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value(tenant));
        }
        failCache();
        for(String tenant:List.of("ent-alpha","ent-beta")) mvc.perform(get(base(tenant)+"/cache.key").with(member("cache-user")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value(tenant));
        jdbc.update("update summer_system_parameter set version=version+1, value_json='\"changed\"' where tenant_id='ent-alpha' and parameter_key='cache.key'");
        mvc.perform(get(base("ent-alpha")+"/cache.key").with(member("cache-user"))).andExpect(status().isServiceUnavailable());
        mvc.perform(get(base("ent-beta")+"/cache.key").with(member("cache-user"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("ent-beta"));
        managed.revokeInterfaces("ENTERPRISE:ent-alpha", "cache-user");
        mvc.perform(get(base("ent-alpha")+"/cache.key").with(member("cache-user"))).andExpect(status().isForbidden());
        mvc.perform(get(base("ent-beta")+"/cache.key").with(member("cache-user"))).andExpect(status().isOk());
    }
    @Test void dictionaryFallbackUsesTheSelectedTenantAndPublishedPointer() throws Exception {
        for(String tenant:List.of("ent-alpha","ent-beta")) {
            policy(tenant,"dictionaries");
            String path="/api/v1/enterprises/"+tenant+"/dictionaries";
            mvc.perform(post(path).with(member()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"cache-dict\",\"name\":\"Cache\",\"publicReadable\":true}")) .andExpect(status().isOk());
            mvc.perform(post(path+"/cache-dict/items").with(member()).contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(mvc, path+"/cache-dict/items", "{\"value\":\"same-value\",\"label\":\""+tenant+"\",\"sortOrder\":1}"))) .andExpect(status().isOk());
            mvc.perform(post(path+"/cache-dict/publish").with(member()).contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(mvc, path+"/cache-dict/publish", "{\"expectedVersion\":0}"))) .andExpect(status().isOk());
            mvc.perform(get(publicDictionary(tenant)).with(member())).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].label").value(tenant));
        }
        failCache();
        for(String tenant:List.of("ent-alpha","ent-beta")) mvc.perform(get(publicDictionary(tenant)).with(member()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].label").value(tenant));
        jdbc.update("update summer_dictionary_type set published=false where tenant_id='ent-alpha' and code='cache-dict'");
        mvc.perform(get(publicDictionary("ent-alpha")).with(member())).andExpect(status().isNotFound());
        mvc.perform(get(publicDictionary("ent-beta")).with(member())).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].label").value("ent-beta"));
    }
    private void policy(String tenant, String resource) {
        managed.configuration(tenant, resource, true, "same-user", "cache-user", "operator");
    }
    private void failCache() { doThrow(new IllegalStateException("simulated dependency outage")).when(cache).get(anyString(),anyString(),anyString(),any(),any()); }
    private static String base(String tenant) { return "/api/v1/enterprises/"+tenant+"/system-parameters"; }
    private static String publicDictionary(String tenant) { return "/public/enterprises/"+tenant+"/dictionaries/cache-dict"; }
}
