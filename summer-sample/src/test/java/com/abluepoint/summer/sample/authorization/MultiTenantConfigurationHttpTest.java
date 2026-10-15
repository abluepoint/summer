package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:multi-config;MODE=MySQL;DB_CLOSE_DELAY=-1", "summer.security.storage.keys=memory", "summer.sample.cache.enabled=false", "summer.sample.resilience.enabled=false"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class MultiTenantConfigurationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired AuthorizationCapabilityRepository capabilities;
    @Autowired AuthorizationRoleRepository roles;
    @Autowired AuthorizationUserBindingRepository users;
    @Test void sameParameterKeyHasIndependentTenantValuesAndHistory() throws Exception {
        for(String tenant:List.of("ent-alpha","ent-beta")) {
            mvc.perform(put("/api/v1/enterprises/"+tenant+"/data-scopes/system-parameters").with(member()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rules\":[{\"type\":\"TENANT\"}]}" )).andExpect(status().isOk());
            mvc.perform(post(base(tenant)).with(member()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"key\":\"shared.key\",\"type\":\"STRING\",\"value\":\""+tenant+"\",\"public\":true}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.data.tenantId").value(tenant));
        }
        for(String tenant:List.of("ent-alpha","ent-beta")) {
            mvc.perform(get(base(tenant)+"/shared.key").with(member())).andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value(tenant));
            mvc.perform(get("/public/enterprises/"+tenant+"/system-parameters/shared.key"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value(tenant));
            mvc.perform(get(base(tenant)+"/shared.key/versions").with(member()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].value").value(tenant));
        }
        for(String tenant:List.of("ent-alpha","ent-beta")) {
            mvc.perform(get(base(tenant)).param("key","shared.key").with(member())).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].value").value(tenant));
            mvc.perform(post(base(tenant)+"/shared.key/draft").with(member()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"value\":\""+tenant+"-draft\",\"baseVersion\":0,\"idempotencyKey\":\"same-draft\"}")) .andExpect(status().isCreated());
            mvc.perform(post(base(tenant)+"/shared.key/publish").with(member()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"revision\":1,\"expectedVersion\":0,\"idempotencyKey\":\"same-publish\"}")) .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value(tenant+"-draft"));
            mvc.perform(post(base(tenant)+"/shared.key/rollback").with(member()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"revision\":0,\"expectedVersion\":1,\"idempotencyKey\":\"same-rollback\"}")) .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value(tenant));
        }
        mvc.perform(post(base("ent-alpha")+"/shared.key/deactivate").with(member()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":2,\"idempotencyKey\":\"disable-alpha\"}")) .andExpect(status().isOk());
        mvc.perform(get("/public/enterprises/ent-alpha/system-parameters/shared.key")).andExpect(status().isNotFound());
        mvc.perform(get("/public/enterprises/ent-beta/system-parameters/shared.key")).andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("ent-beta"));
        String foreignEvent=null;
        for(String tenant:List.of("ent-alpha","ent-beta")) {
            var result=mvc.perform(get("/api/v1/enterprises/"+tenant+"/system-parameter-events").param("key","shared.key").with(member()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(tenant.equals("ent-alpha")?4:3))
                    .andExpect(jsonPath("$.data.items[*].tenantId",org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(tenant)))).andReturn();
            if(tenant.equals("ent-alpha")) foreignEvent=com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),"$.data.items[0].eventId");
        }
        mvc.perform(post("/api/v1/enterprises/ent-beta/system-parameter-events/"+foreignEvent+"/retry").with(member())).andExpect(status().isNotFound());
        mvc.perform(get(base("ent-foreign")+"/shared.key").with(member())).andExpect(status().isForbidden());
        mvc.perform(get("/public/system-parameters/shared.key")).andExpect(status().isForbidden());
        mvc.perform(get("/api/system-parameters/shared.key").with(member())).andExpect(status().isForbidden());
    }
    @Test void sameDictionaryCodeAndItemValuePublishIndependentSnapshots() throws Exception {
        for(String tenant:List.of("ent-alpha","ent-beta")) {
            mvc.perform(put("/api/v1/enterprises/"+tenant+"/data-scopes/dictionaries").with(member()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rules\":[{\"type\":\"TENANT\"}]}" )).andExpect(status().isOk());
            String path="/api/v1/enterprises/"+tenant+"/dictionaries";
            mvc.perform(post(path).with(member()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"shared-dict\",\"name\":\"Shared\",\"publicReadable\":true}")) .andExpect(status().isOk());
            mvc.perform(post(path+"/shared-dict/items").with(member()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"value\":\"same-value\",\"label\":\""+tenant+"\",\"sortOrder\":1}")) .andExpect(status().isOk());
            mvc.perform(post(path+"/shared-dict/publish").with(member()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedVersion\":0,\"idempotencyKey\":\"same-publish\"}")) .andExpect(status().isOk());
        }
        for(String tenant:List.of("ent-alpha","ent-beta")) {
            mvc.perform(get("/public/enterprises/"+tenant+"/dictionaries/shared-dict"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].label").value(tenant));
            mvc.perform(get("/api/v1/enterprises/"+tenant+"/dictionaries/shared-dict/versions").with(member()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        }
    }
    @Test void overrideDraftPublishDisableAndRollbackHaveIndependentHistory() throws Exception {
        String base=base("ent-alpha");
        mvc.perform(put("/api/v1/enterprises/ent-alpha/data-scopes/system-parameters").with(member()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rules\":[{\"type\":\"TENANT\"}]}" )).andExpect(status().isOk());
        mvc.perform(post(base).with(member()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"lifecycle.key\",\"type\":\"STRING\",\"value\":\"base\",\"public\":true}")) .andExpect(status().isCreated());
        String scope=base+"/lifecycle.key/overrides/_tenant";
        String published="/public/enterprises/ent-alpha/system-parameters/lifecycle.key";
        mvc.perform(post(scope+"/draft").with(member()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"first\",\"publicReadable\":true}")) .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(1));
        mvc.perform(get(published)).andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("base"));
        mvc.perform(post(scope+"/publish").with(member()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":1,\"expectedVersion\":0}")) .andExpect(status().isOk());
        mvc.perform(get(published)).andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("first"));
        mvc.perform(post(scope+"/disable").with(member()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":1}")) .andExpect(status().isOk());
        mvc.perform(get(published)).andExpect(status().isNotFound());
        mvc.perform(post(scope+"/rollback").with(member()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":2,\"expectedVersion\":2}")) .andExpect(status().isOk());
        mvc.perform(get(published)).andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("first"));
        mvc.perform(post(scope+"/disable").with(member()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":1}")) .andExpect(status().isConflict());
        mvc.perform(get(scope+"/versions").with(member())).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(4));
        mvc.perform(get(base+"/lifecycle.key/versions").with(member())).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(put("/api/v1/enterprises/ent-beta/data-scopes/system-parameters").with(member()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rules\":[{\"type\":\"TENANT\"}]}" )).andExpect(status().isOk());
        mvc.perform(get(scope.replace("ent-alpha","ent-beta")+"/versions").with(member())).andExpect(status().isNotFound());
    }
    @Test void foreignOrganizationCannotBeUsedForCreationOrCurrentContext() throws Exception {
        jdbc.update("insert into summer_organization_unit(unit_id,enterprise_id,unit_type,name,code,sort_order,status,created_at,updated_at) values('multi-alpha-org','ent-alpha','DEPARTMENT','Alpha','alpha',0,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for(String tenant:List.of("ent-alpha","ent-beta")) {
            mvc.perform(put("/api/v1/enterprises/"+tenant+"/data-scopes/system-parameters").with(member()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rules\":[{\"type\":\"TENANT\"}]}" )).andExpect(status().isOk());
        }
        mvc.perform(post(base("ent-beta")).with(member()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"foreign.org\",\"type\":\"STRING\",\"value\":\"bad\",\"organizationId\":\"multi-alpha-org\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(base("ent-beta")).with(member()).with(jwt().jwt(j->j.subject("same-user")
                .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-beta","status","ACTIVE")))
                .claim("organization_id","multi-alpha-org")).authorities(new SimpleGrantedAuthority("SCOPE_system.parameter.read"))))
                .andExpect(status().isForbidden());
    }
    @Test void persistedRoleAndUserPoliciesAgreeInPreviewSqlAndJpaWithoutCrossTenantEffects() throws Exception {
        for(String tenant:List.of("ent-policy-a","ent-policy-b")) {
            mvc.perform(put("/api/v1/enterprises/"+tenant+"/data-scopes/system-parameters").with(member()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rules\":[{\"type\":\"TENANT\"}]}" )).andExpect(status().isOk());
            mvc.perform(post(base(tenant)).with(member("policy-user")).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"key\":\"policy.key\",\"type\":\"STRING\",\"value\":\""+tenant+"\"}")) .andExpect(status().isCreated());
            capabilities.saveAndFlush(new AuthorizationCapabilityEntity("multi-config-cap",null,"ENTERPRISE","multi.config.parameter.read","system.parameter.read","read","LOW","ACTIVE",1));
            roles.saveAndFlush(new AuthorizationRoleEntity(tenant+"-role",tenant,"Reader","same-role-code",false,"ACTIVE","multi-config-cap","system.parameter.read",null,1));
            users.saveAndFlush(new AuthorizationUserBindingEntity(tenant+"-binding",tenant,"policy-user","[\""+tenant+"-role\"]","[]",1));
            mvc.perform(put("/api/v1/enterprises/"+tenant+"/data-scopes/system-parameters/bindings/ROLE/"+tenant+"-role").with(member()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rules\":[{\"type\":\"TENANT\"}]}" )).andExpect(status().isOk());
        }
        mvc.perform(put("/api/v1/enterprises/ent-policy-a/data-scopes/system-parameters/bindings/USER/policy-user").with(member()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rules\":[{\"type\":\"TENANT\"}]}" )).andExpect(status().isOk());
        for(String tenant:List.of("ent-policy-a","ent-policy-b")) {
            boolean allowed=tenant.endsWith("b");
            mvc.perform(get(base(tenant)).with(member("policy-user"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(allowed?1:0));
            mvc.perform(get(base(tenant)+"/policy.key").with(member("policy-user"))).andExpect(status().is(allowed?200:404));
            mvc.perform(post("/api/v1/enterprises/"+tenant+"/data-scopes/system-parameters:preview").with(member()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"subject\":\"policy-user\",\"creatorId\":\"policy-user\"}")) .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowed").value(allowed));
        }
    }
    @Test void dictionaryCreationUsesValidatedOrganizationWithinSelectedTenant() throws Exception {
        jdbc.update("insert into summer_organization_unit(unit_id,enterprise_id,unit_type,name,code,sort_order,status,created_at,updated_at) values('dict-alpha-org','ent-alpha','DEPARTMENT','Alpha','dict-alpha',0,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for(String tenant:List.of("ent-alpha","ent-beta")) {
            mvc.perform(put("/api/v1/enterprises/"+tenant+"/data-scopes/dictionaries").with(member()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rules\":[{\"type\":\"TENANT\"}]}" )).andExpect(status().isOk());
            mvc.perform(post("/api/v1/enterprises/"+tenant+"/dictionaries").with(member()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"org-dict\",\"name\":\"Organization\",\"organizationId\":\"dict-alpha-org\"}"))
                    .andExpect(status().is(tenant.equals("ent-alpha")?200:403));
        }
    }
    @Test void overrideLifecycleActionsAreAuthorizedSeparately() throws Exception {
        mvc.perform(put("/api/v1/enterprises/ent-alpha/data-scopes/system-parameters").with(member()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rules\":[{\"type\":\"TENANT\"}]}" )).andExpect(status().isOk());
        mvc.perform(post(base("ent-alpha")).with(member()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"action.override\",\"type\":\"STRING\",\"value\":\"base\",\"public\":true}")) .andExpect(status().isCreated());
        String scope=base("ent-alpha")+"/action.override/overrides/_tenant";
        mvc.perform(post(scope+"/draft").with(member()).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"override\",\"publicReadable\":true}")) .andExpect(status().isOk());
        mvc.perform(post(scope+"/publish").with(actionScope("update")).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1,\"expectedVersion\":0}")) .andExpect(status().isForbidden());
        mvc.perform(post(scope+"/publish").with(actionScope("publish")).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1,\"expectedVersion\":0}")) .andExpect(status().isOk());
        mvc.perform(post(scope+"/disable").with(actionScope("publish")).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}")) .andExpect(status().isForbidden());
        mvc.perform(post(scope+"/disable").with(actionScope("disable")).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}")) .andExpect(status().isOk());
        mvc.perform(post(scope+"/rollback").with(actionScope("publish")).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":2,\"expectedVersion\":2}")) .andExpect(status().isForbidden());
        mvc.perform(post(scope+"/rollback").with(actionScope("rollback")).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":2,\"expectedVersion\":2}")) .andExpect(status().isOk());
        mvc.perform(get(scope+"/versions").with(actionScope("write"))).andExpect(status().isForbidden());
        mvc.perform(get(scope+"/versions").with(actionScope("read"))).andExpect(status().isOk());
    }
    private static RequestPostProcessor actionScope(String action) {
        return jwt().jwt(j->j.subject("action-user").claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-alpha","status","ACTIVE"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_system.parameter."+action));
    }
    @Test void conflictingRepeatedTenantHeadersAreRejectedBeforeBusinessLookup() throws Exception {
        mvc.perform(put("/api/v1/enterprises/ent-alpha/data-scopes/system-parameters").with(member()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rules\":[{\"type\":\"TENANT\"}]}" )).andExpect(status().isOk());
        mvc.perform(get(base("ent-alpha")).header("X-Enterprise-Id","ent-alpha","ent-beta").with(member())).andExpect(status().isForbidden());
    }
    static String base(String tenant) { return "/api/v1/enterprises/"+tenant+"/system-parameters"; }
    static RequestPostProcessor member() { return member("same-user"); }
    static RequestPostProcessor member(String subject) {
        var caps=List.of("system.parameter.read","system.parameter.write","platform.dictionary.read","platform.dictionary.write","enterprise.data-scope.read","enterprise.data-scope.write");
        return jwt().jwt(j->j.subject(subject).claim("enterprise_memberships",List.of("ent-alpha","ent-beta","ent-policy-a","ent-policy-b").stream().map(t->Map.of("enterpriseId",t,"status","ACTIVE")).toList())
                .claim("enterprise_capabilities",Map.of("ent-alpha",caps,"ent-beta",caps,"ent-policy-a",caps,"ent-policy-b",caps)))
                .authorities(caps.stream().map(c->new SimpleGrantedAuthority("SCOPE_"+c)).toArray(SimpleGrantedAuthority[]::new));
    }
}
