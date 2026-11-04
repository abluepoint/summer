package com.abluepoint.summer.sample.resilience;

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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:resilient-configuration;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "summer.security.storage.keys=memory", "summer.sample.resilience.enabled=true", "summer.sample.resilience.timeout=5s", "summer.sample.resilience.max-attempts=1"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ResilientConfigurationHttpTest {
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
        failure(()->jdbc.execute("alter table summer_data_scope_policy rename to unavailable_scope_policy"));
        try { mvc.perform(get("/api/system-parameters/resilient.test").with(actor())).andExpect(status().isServiceUnavailable()); }
        finally { jdbc.execute("alter table unavailable_scope_policy rename to summer_data_scope_policy"); }
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
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
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
    @Test void changedPermissionDuringFailureInvalidatesFallback() throws Exception {
        parameter();
        // A SELF policy is established before warming under that policy.
        mvc.perform(put("/api/v1/enterprises/ent-acme/data-scopes/system-parameters").with(actor())
                .contentType(MediaType.APPLICATION_JSON).content("{\"rules\":[{\"type\":\"SELF\"}]}")) .andExpect(status().isOk());
        mvc.perform(get("/api/system-parameters/resilient.test").with(actor())).andExpect(status().isOk());
        failure(()->jdbc.update("update summer_system_parameter set created_by='someone-else' where parameter_key='resilient.test'"));
        mvc.perform(get("/api/system-parameters/resilient.test").with(actor())).andExpect(status().isServiceUnavailable());
    }
    @Test void interfaceDenialDuringFailureDoesNotBecomeFallbackSuccess() throws Exception {
        parameter();
        failure(() -> jdbc.update("insert into summer_authorization_user_binding "
                + "(binding_id,enterprise_id,username,role_ids,denied_capability_codes,version,created_at,updated_at) "
                + "values ('deny','ent-acme','scope-reader','','system.parameter.read',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"));
        mvc.perform(get("/api/system-parameters/resilient.test").with(actor())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data").doesNotExist());
    }
    private void dictionary() throws Exception {
        mvc.perform(post("/api/dictionaries").with(actor()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"resilient-dict\",\"name\":\"Resilient\",\"publicReadable\":true}")) .andExpect(status().isOk());
        mvc.perform(post("/api/dictionaries/resilient-dict/items").with(actor()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"option\",\"label\":\"Published\",\"sortOrder\":1}")) .andExpect(status().isOk());
        mvc.perform(post("/api/dictionaries/resilient-dict/publish").with(actor()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0}")) .andExpect(status().isOk());
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
        var caps=List.of("system.parameter.read","system.parameter.write","platform.dictionary.read","platform.dictionary.write","enterprise.data-scope.read","enterprise.data-scope.write");
        return jwt().jwt(j->j.subject("scope-reader").claim("organization_id","org-engineering")
                .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE")))
                .claim("enterprise_capabilities",Map.of("ent-acme",caps)))
                .authorities(caps.stream().map(v->new SimpleGrantedAuthority("SCOPE_"+v)).toArray(SimpleGrantedAuthority[]::new));
    }
}
