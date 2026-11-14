package com.abluepoint.summer.sample.resilience;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import com.abluepoint.summer.sample.cache.VersionedTwoLevelCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:resilient-configuration;MODE=MySQL;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE", "summer.security.storage.keys=memory", "summer.sample.resilience.enabled=true", "summer.sample.resilience.timeout=5s", "summer.sample.resilience.max-attempts=1"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class ResilientConfigurationHttpTest {
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;

    @Autowired org.springframework.transaction.PlatformTransactionManager grantTransactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources grantResources;
    private com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture dataGrants;

    @org.junit.jupiter.api.AfterEach
    void releaseCurrentDataGrants() { if (dataGrants != null) dataGrants.close(); }
    private String parameterReadRole;

    @org.junit.jupiter.api.BeforeEach
    void currentAuthorization() throws Exception {
        dataGrants = new com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture(jdbc, grantTransactions, grantResources);
        dataGrants.data("ent-acme", "scope-reader", "system-parameters", "read", "TENANT_ALL");
        dataGrants.data("ent-acme", "scope-reader", "system-parameters", "create", "TENANT_ALL");
        dataGrants.data("ent-acme", "scope-reader", "system-parameters", "update", "TENANT_ALL");
        dataGrants.data("ent-acme", "scope-reader", "dictionaries", "read", "TENANT_ALL");
        dataGrants.data("ent-acme", "scope-reader", "dictionaries", "create", "TENANT_ALL");
        dataGrants.data("ent-acme", "scope-reader", "dictionaries", "update", "TENANT_ALL");
        dataGrants.data("ent-acme", "operator", "dictionaries", "read", "TENANT_ALL");
        current.grantHandlers(Domain.enterprise("ent-acme"), "scope-reader", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.systemparameter.SystemParameterController")
                        && java.util.Set.of("POST /api/system-parameters").contains(method + " " + path));
        parameterReadRole = current.grantHandlers(Domain.enterprise("ent-acme"), "scope-reader", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.systemparameter.SystemParameterController")
                        && java.util.Set.of("GET /api/system-parameters/{key}").contains(method + " " + path));
        current.grantHandlers(Domain.enterprise("ent-acme"), "scope-reader", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.dictionary.DictionaryController")
                        && java.util.Set.of(
                                "POST /api/dictionaries",
                                "POST /api/dictionaries/{code}/items",
                                "POST /api/dictionaries/{code}/publish",
                                "POST /api/dictionaries/{code}/disable",
                                "GET /public/dictionaries/{code}")
                        .contains(method + " " + path));
        current.grantHandlers(Domain.enterprise("ent-acme"), "operator", (handler, path, method) ->
                handler.getBeanType().getName().equals("com.abluepoint.summer.sample.dictionary.DictionaryController")
                        && java.util.Set.of("GET /api/dictionaries/{code}/draft").contains(method + " " + path));
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean VersionedTwoLevelCache cache;
    @Test void changedVersionCannotUsePreviouslyWarmParameterFallback() throws Exception {
        parameter();
        jdbc.update("update summer_system_parameter set value_json='\"v2\"', version=version+1 where parameter_key='resilient.test'");
        failure(()->{});
        mvc.perform(get("/api/system-parameters/resilient.test").with(actor())).andExpect(status().isServiceUnavailable());
    }
    @Test void disablingDuringDependencyFailureInvalidatesTheWarmFallback() throws Exception {
        parameter();
        failure(()->jdbc.update("update summer_system_parameter set status='INACTIVE',version=version+1 where parameter_key='resilient.test'"));
        mvc.perform(get("/api/system-parameters/resilient.test").with(actor())).andExpect(status().isServiceUnavailable());
    }
    @Test void unverifiedPolicyCannotReturnWarmData() throws Exception {
        parameter();
        failure(()->jdbc.execute("alter table summer_permission_data_scope rename to unavailable_current_data_scope"));
        try { mvc.perform(get("/api/system-parameters/resilient.test").with(actor())).andExpect(status().isServiceUnavailable()); }
        finally { jdbc.execute("alter table unavailable_current_data_scope rename to summer_permission_data_scope"); }
    }
    @Test void verifiedUnchangedParameterCanFallbackAndRecover() throws Exception {
        parameter();
        failure(()->{});
        mvc.perform(get("/api/system-parameters/resilient.test").with(actor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("v1"));
        reset(cache);
        jdbc.update("update summer_system_parameter set value_json='\"v2\"',version=version+1 where parameter_key='resilient.test'");
        mvc.perform(get("/api/system-parameters/resilient.test").with(actor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("v2"));
    }
    @Test void withdrawingPublicFlagDuringFailureInvalidatesParameterFallback() throws Exception {
        parameter();
        failure(()->jdbc.update("update summer_system_parameter set public_flag=false,version=version+1 where parameter_key='resilient.test'"));
        mvc.perform(get("/api/system-parameters/resilient.test").with(actor())).andExpect(status().isServiceUnavailable());
    }
    @Test void verifiedPublishedDictionaryCanFallback() throws Exception {
        dictionary();
        failure(()->{});
        mvc.perform(get("/public/dictionaries/resilient-dict").with(actor())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].label").value("Published"));
    }
    @Test void withdrawingDictionaryDuringFailureCannotReturnSnapshot() throws Exception {
        dictionary();
        failure(() -> {
            try {
                mvc.perform(post("/api/dictionaries/resilient-dict/disable").with(actor())
                        .contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(mvc, "/api/dictionaries/resilient-dict/disable", "{\"expectedVersion\":1}")))
                        .andExpect(status().isOk());
            } catch (Exception exception) { throw new IllegalStateException(exception); }
        });
        mvc.perform(get("/public/dictionaries/resilient-dict").with(actor())).andExpect(status().isServiceUnavailable());
    }
    @Test void changingPublishedPointerDuringFailureCannotReturnObsoleteSnapshot() throws Exception {
        dictionary();
        failure(()->jdbc.update("update summer_dictionary_type set published_version=2 where code='resilient-dict'"));
        mvc.perform(get("/public/dictionaries/resilient-dict").with(actor())).andExpect(status().isServiceUnavailable());
    }
    @Test void samePublishedPointerWithNewEffectiveRevisionCannotFallback() throws Exception {
        dictionary();
        failure(() -> jdbc.update("update summer_dictionary_type set effective_revision=effective_revision+2 where code='resilient-dict'"));
        mvc.perform(get("/public/dictionaries/resilient-dict").with(actor())).andExpect(status().isServiceUnavailable());
    }
    @Test void changedPermissionDuringFailureInvalidatesFallback() throws Exception {
        parameter();
        // Replace only this actor's current read preset before warming.
        dataGrants.data("ent-acme", "scope-reader", "system-parameters", "read", "SELF");
        mvc.perform(get("/api/system-parameters/resilient.test").with(actor())).andExpect(status().isOk());
        failure(()->jdbc.update("update summer_system_parameter set owner_id='someone-else' where parameter_key='resilient.test'"));
        mvc.perform(get("/api/system-parameters/resilient.test").with(actor())).andExpect(status().isServiceUnavailable());
    }
    @Test void interfaceDenialDuringFailureDoesNotBecomeFallbackSuccess() throws Exception {
        parameter();
        failure(() -> current.revoke(Domain.enterprise("ent-acme"), "scope-reader", parameterReadRole));
        mvc.perform(get("/api/system-parameters/resilient.test").with(actor())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data").doesNotExist());
    }
    private void dictionary() throws Exception {
        mvc.perform(post("/api/dictionaries").with(actor()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"resilient-dict\",\"name\":\"Resilient\",\"publicReadable\":true}")) .andExpect(status().isOk());
        mvc.perform(post("/api/dictionaries/resilient-dict/items").with(actor()).contentType(MediaType.APPLICATION_JSON)
                .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(mvc, "/api/dictionaries/resilient-dict/items", "{\"value\":\"option\",\"label\":\"Published\",\"sortOrder\":1}"))) .andExpect(status().isOk());
        mvc.perform(post("/api/dictionaries/resilient-dict/publish").with(actor()).contentType(MediaType.APPLICATION_JSON)
                .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(mvc, "/api/dictionaries/resilient-dict/publish", "{\"expectedVersion\":0}"))) .andExpect(status().isOk());
        mvc.perform(get("/public/dictionaries/resilient-dict").with(actor())).andExpect(status().isOk());
    }
    private void parameter() throws Exception {
        mvc.perform(post("/api/system-parameters").with(actor()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"resilient.test\",\"type\":\"STRING\",\"value\":\"v1\",\"public\":true}")) .andExpect(status().isCreated());
        mvc.perform(get("/api/system-parameters/resilient.test").with(actor())).andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("v1"));
    }
    private void failure(Runnable mutation) {
        AtomicBoolean first=new AtomicBoolean(true);
        doAnswer(call->{if(first.getAndSet(false))mutation.run();throw new IllegalStateException("injected dependency outage");})
                .when(cache).get(anyString(),anyString(),anyString(),any(),any());
    }
    private static RequestPostProcessor actor() {
        var caps=List.of("system.parameter.read","system.parameter.write","platform.dictionary.read","platform.dictionary.write");
        return jwt().jwt(j->j.claim("scope", String.join(" ", caps)).subject("scope-reader").claim("organization_id","org-engineering")
                .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE"))))
                .authorities(caps.stream().map(v->new SimpleGrantedAuthority("SCOPE_"+v)).toArray(SimpleGrantedAuthority[]::new));
    }
}
