package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"summer.security.storage.keys=memory", "summer.sample.cache.enabled=false", "summer.sample.resilience.enabled=false"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthorizedConfigurationHttpTest {
    // These legacy fixtures now declare their enterprise; production has no implicit sample tenant.
    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))));
    }

    @Autowired MockMvc mvc;

    @Autowired org.springframework.jdbc.core.JdbcTemplate managedDb;
    @Autowired org.springframework.transaction.PlatformTransactionManager managedTransactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources managedInterfaces;
    private com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture managed;
    @org.junit.jupiter.api.AfterEach void releaseManagedBusinessFixture() { if (managed != null) managed.close(); }

    private String positiveBobPriorStatus;
    @org.junit.jupiter.api.AfterEach void restorePositiveBobMembership() {
        if (positiveBobPriorStatus != null)
            managedDb.update("update summer_enterprise_member set status=? where enterprise_id='ent-acme' and subject='bob'", positiveBobPriorStatus);
    }

    @org.junit.jupiter.api.BeforeEach void prepareCurrentBusinessAuthorization() {
        // This scenario explicitly requires an active owner; the shared identity fixture never revives disabled members.
        positiveBobPriorStatus = managedDb.queryForObject("select status from summer_enterprise_member where enterprise_id='ent-acme' and subject='bob'", String.class);
        managedDb.update("update summer_enterprise_member set status='ACTIVE' where enterprise_id='ent-acme' and subject='bob'");
        managed = new BusinessManagedAuthorizationTestFixture(managedDb, managedTransactions, managedInterfaces);
        for (String resource : List.of("system-parameters", "dictionaries"))
            managed.configuration("ent-acme", resource, true, "alice", "bob", "operator");
        managed.identity("ENTERPRISE:ent-other", "foreign");
    }

    @Test void publicParametersRequireBothPublicMarkerAndCurrentScope() throws Exception {
        create("visible.param", true); create("hidden.param", false);
        mvc.perform(get("/public/enterprises/ent-acme/system-parameters/visible.param")).andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("base"));
        mvc.perform(get("/public/enterprises/ent-acme/system-parameters/hidden.param")).andExpect(status().isNotFound()).andExpect(jsonPath("$.data").doesNotExist());
        policy("system-parameters", "SELF");
        mvc.perform(get("/public/enterprises/ent-acme/system-parameters/visible.param").with(member("alice", "org-engineering"))).andExpect(status().isOk());
        mvc.perform(get("/public/enterprises/ent-acme/system-parameters/visible.param").with(member("bob", "org-engineering"))).andExpect(status().isNotFound());
        mvc.perform(get("/public/enterprises/ent-acme/system-parameters/visible.param")).andExpect(status().isNotFound());
    }
    @Test void publicDictionarySnapshotChecksTheCurrentSubjectAndItemScope() throws Exception {
        mvc.perform(post("/api/dictionaries").with(member("alice", "org-engineering")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"scope-public\",\"name\":\"Scope\",\"publicReadable\":true}")) .andExpect(status().isOk());
        mvc.perform(post("/api/dictionaries/scope-public/items").with(member("bob", "org-engineering")).contentType(MediaType.APPLICATION_JSON)
                .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(mvc, "/api/dictionaries/scope-public/items", "{\"value\":\"hidden\",\"label\":\"Hidden option\",\"sortOrder\":1}"))) .andExpect(status().isOk());
        mvc.perform(post("/api/dictionaries/scope-public/publish").with(member("alice", "org-engineering")).contentType(MediaType.APPLICATION_JSON)
                .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(mvc, "/api/dictionaries/scope-public/publish", "{\"expectedVersion\":0}"))) .andExpect(status().isOk());
        policy("dictionaries", "SELF");
        mvc.perform(get("/public/enterprises/ent-acme/dictionaries/scope-public").with(member("alice", "org-engineering")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/public/enterprises/ent-acme/dictionaries/scope-public").with(member("bob", "org-engineering"))).andExpect(status().isNotFound());
        mvc.perform(get("/public/enterprises/ent-acme/dictionaries/scope-public")).andExpect(status().isNotFound());
    }
    @Test void creationPermissionDoesNotPermitModificationOrPublishing() throws Exception {
        mvc.perform(post("/api/system-parameters").with(scopes("system.parameter.create")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"actions.param\",\"type\":\"STRING\",\"value\":\"base\"}")) .andExpect(status().isCreated());
        mvc.perform(patch("/api/system-parameters/actions.param").with(scopes("system.parameter.create")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"changed\",\"version\":0}")) .andExpect(status().isForbidden());
        mvc.perform(post("/api/system-parameters/actions.param/publish").with(scopes("system.parameter.update")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"revision\":0,\"expectedVersion\":0,\"idempotencyKey\":\"blocked\"}")) .andExpect(status().isForbidden());
    }
    @Test void organizationOverrideWinsThenTenantOverrideAndNonpublicOverrideFailsClosed() throws Exception {
        create("override.param", true);
        override("_tenant", "tenant", true, null);
        override("org-engineering", "organization", true, null);
        mvc.perform(get("/public/enterprises/ent-acme/system-parameters/override.param").with(member("alice", "org-engineering")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("organization"));
        mvc.perform(get("/public/enterprises/ent-acme/system-parameters/override.param").with(member("alice", "org-operations")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value("tenant"));
        override("org-engineering", "private", false, 0L);
        mvc.perform(get("/public/enterprises/ent-acme/system-parameters/override.param").with(member("alice", "org-engineering"))).andExpect(status().isNotFound());
    }
    @Test void updatesCannotSmuggleDisableOrPublishActionsThroughOtherFields() throws Exception {
        create("actions.update", true);
        mvc.perform(post("/api/system-parameters").with(scopes("system.parameter.create"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"key\":\"smuggled.public\",\"type\":\"STRING\",\"value\":\"base\",\"public\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/system-parameters/actions.update").with(scopes("system.parameter.update", "system.parameter.publish"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\",\"version\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/dictionaries").with(scopes("platform.dictionary.create"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"smuggled-publish\",\"name\":\"Draft\",\"published\":true,\"publicReadable\":true}"))
                .andExpect(status().isForbidden());
        for (String action : List.of("publish", "disable", "rollback")) {
            mvc.perform(post("/api/dictionaries/anything/"+action).with(scopes("platform.dictionary.update"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                    .andExpect(status().isForbidden());
        }
    }
    @Test void explicitForeignTenantMembershipCannotReadPublicOrManageConfiguration() throws Exception {
        create("tenant.param", true);
        var foreign=jwt().jwt(j -> j.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).subject("foreign").claim("scope", "system.parameter.read").claim("enterprise_memberships", List.of(Map.of("enterpriseId","ent-other","status","ACTIVE"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_system.parameter.read"));
        mvc.perform(get("/public/enterprises/ent-acme/system-parameters/tenant.param").with(foreign)).andExpect(status().isForbidden());
        mvc.perform(get("/api/system-parameters").with(foreign)).andExpect(status().isForbidden());
    }
    @Test void dictionaryOverridesAreScopedVersionedAndValidatedAgainstVisibleItems() throws Exception {
        mvc.perform(post("/api/dictionaries").with(member("alice", "org-engineering")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"override-dict\",\"name\":\"Override\",\"published\":false,\"publicReadable\":true}")) .andExpect(status().isOk());
        mvc.perform(post("/api/dictionaries/override-dict/items").with(member("alice", "org-engineering")).contentType(MediaType.APPLICATION_JSON)
                .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(mvc, "/api/dictionaries/override-dict/items", "{\"value\":\"option\",\"label\":\"Base\",\"sortOrder\":1}"))) .andExpect(status().isOk());
        mvc.perform(post("/api/dictionaries/override-dict/publish").with(member("alice", "org-engineering"))
                .contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(mvc, "/api/dictionaries/override-dict/publish", "{\"expectedVersion\":0}"))) .andExpect(status().isOk());
        String payload="{\"value\":[{\"value\":\"option\",\"label\":\"Org\",\"sortOrder\":1,\"enabled\":true}],\"publicReadable\":true}";
        mvc.perform(put("/api/dictionaries/override-dict/overrides/org-engineering").with(member("alice","org-engineering"))
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(0));
        mvc.perform(get("/public/enterprises/ent-acme/dictionaries/override-dict").with(member("alice","org-engineering")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].label").value("Org"));
        mvc.perform(get("/public/enterprises/ent-acme/dictionaries/override-dict").with(member("alice","org-operations")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].label").value("Base"));
        mvc.perform(post("/api/dictionaries/override-dict/overrides/org-engineering/draft").with(member("alice","org-engineering"))
                .contentType(MediaType.APPLICATION_JSON).content(payload.replace("Org", "Draft").replace("\"publicReadable\":true", "\"expectedVersion\":0,\"publicReadable\":true")))
                .andExpect(status().isOk());
        mvc.perform(get("/public/enterprises/ent-acme/dictionaries/override-dict").with(member("alice","org-engineering")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].label").value("Org"))
                .andExpect(jsonPath("$.data.publishedVersion").value(1)).andExpect(jsonPath("$.data.publishedRevision").value(1));
        mvc.perform(post("/api/dictionaries/override-dict/items").with(member("alice","org-engineering"))
                .contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(mvc, "/api/dictionaries/override-dict/items", "{\"value\":\"draft-only\",\"label\":\"Draft\",\"sortOrder\":2}")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/dictionaries/override-dict/overrides/_tenant/draft").with(member("alice","org-engineering"))
                .contentType(MediaType.APPLICATION_JSON).content(payload.replace("option", "draft-only")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/dictionaries/override-dict/overrides/org-engineering").with(member("alice","org-engineering"))
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isConflict());
        mvc.perform(put("/api/dictionaries/override-dict/overrides/org-operations").with(member("alice","org-operations"))
                .contentType(MediaType.APPLICATION_JSON).content(payload.replace("option", "nonexistent"))).andExpect(status().isBadRequest());
    }

    @Test void publicApiHonorsCurrentInterfaceRevocationWithoutTrustingTokenScopes() throws Exception {
        mvc.perform(post("/api/dictionaries").with(member("alice", "org-engineering")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"interface-revoked-public\",\"name\":\"Public interface\",\"publicReadable\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/dictionaries/interface-revoked-public/items").with(member("alice", "org-engineering")).contentType(MediaType.APPLICATION_JSON)
                .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(mvc, "/api/dictionaries/interface-revoked-public/items", "{\"value\":\"visible\",\"label\":\"Visible\",\"sortOrder\":1}")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/dictionaries/interface-revoked-public/publish").with(member("alice", "org-engineering")).contentType(MediaType.APPLICATION_JSON)
                .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(mvc, "/api/dictionaries/interface-revoked-public/publish", "{\"expectedVersion\":0}")))
                .andExpect(status().isOk());
        var actor = member("alice", "org-engineering");
        mvc.perform(get("/public/enterprises/ent-acme/dictionaries/interface-revoked-public").with(actor))
                .andExpect(status().isOk());
        managed.revokeInterfaces("ENTERPRISE:ent-acme", "alice");
        mvc.perform(get("/public/enterprises/ent-acme/dictionaries/interface-revoked-public").with(actor))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test void publicParameterHonorsCurrentInterfaceRevocationWithTheSameToken() throws Exception {
        create("denied.public", true);
        var actor = member("alice", "org-engineering");
        String path = "/public/enterprises/ent-acme/system-parameters/denied.public";
        mvc.perform(get(path).with(actor)).andExpect(status().isOk());
        managed.revokeInterfaces("ENTERPRISE:ent-acme", "alice");
        mvc.perform(get(path).with(actor)).andExpect(status().isForbidden()).andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test void publicParameterHonorsCurrentDataRevocationWithTheSameToken() throws Exception {
        create("denied.public", true);
        var actor = member("alice", "org-engineering");
        String path = "/public/enterprises/ent-acme/system-parameters/denied.public";
        mvc.perform(get(path).with(actor)).andExpect(status().isOk());
        managed.revokeRead("ent-acme", "alice", "system-parameters");
        managed.revokePublicRead("ent-acme", "system-parameters");
        mvc.perform(get(path).with(actor)).andExpect(status().isNotFound()).andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get(path)).andExpect(status().isNotFound());
        managed.data("ent-acme", "alice", "system-parameters", "read", "TENANT_ALL");
        mvc.perform(get(path).with(actor)).andExpect(status().isOk());
        mvc.perform(get(path)).andExpect(status().isNotFound());
    }

    @Test void missingModeCannotBeRecreatedByAnOrdinaryScopedBusinessRequest() throws Exception {
        String missing = "ent-business10-unregistered";
        org.junit.jupiter.api.Assertions.assertEquals(0, managedDb.queryForObject(
                "select count(*) from summer_authorization_domain where domain_key=?", Integer.class, "ENTERPRISE:" + missing));
        var actor = jwt().jwt(j -> j.subject("alice").claim("scope", "system.parameter.read")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", missing, "status", "ACTIVE"))));
        mvc.perform(get("/api/v1/enterprises/" + missing + "/system-parameters").with(actor))
                .andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertEquals(0, managedDb.queryForObject(
                "select count(*) from summer_authorization_domain where domain_key=?", Integer.class, "ENTERPRISE:" + missing));
        org.junit.jupiter.api.Assertions.assertEquals(0, managedDb.queryForObject(
                "select count(*) from summer_permission where domain_key=?", Integer.class, "ENTERPRISE:" + missing));
    }

    private void override(String org, String value, boolean pub, Long version) throws Exception {
        mvc.perform(put("/api/system-parameters/override.param/overrides/"+org).with(member("alice", "org-engineering"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\""+value+"\",\"publicReadable\":"+pub+",\"expectedVersion\":"+version+"}")) .andExpect(status().isOk());
    }
    private void create(String key, boolean pub) throws Exception {
        mvc.perform(post("/api/system-parameters").with(member("alice", "org-engineering")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\""+key+"\",\"type\":\"STRING\",\"value\":\"base\",\"public\":"+pub+"}")) .andExpect(status().isCreated());
    }
    private void policy(String resource, String field) throws Exception {
        for (String subject : List.of("alice", "bob", "operator"))
            managed.data("ent-acme", subject, resource, "read", field);
        managed.publicData("ent-acme", resource, field);
    }
    private static RequestPostProcessor scopes(String... scopes) {
        return jwt().jwt(j -> j.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).subject("alice").claim("scope", String.join(" ", scopes))).authorities(Arrays.stream(scopes).map(v->new SimpleGrantedAuthority("SCOPE_"+v)).toArray(SimpleGrantedAuthority[]::new));
    }
    private static RequestPostProcessor member(String subject,String org) {
        List<String> caps=List.of("system.parameter.read","system.parameter.write","platform.dictionary.read","platform.dictionary.write","enterprise.data-scope.read","enterprise.data-scope.write");
        return jwt().jwt(j -> j.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).subject(subject).claim("scope", String.join(" ", caps)).claim("organization_id",org).claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE"))).claim("enterprise_capabilities",Map.of("ent-acme",caps)))
                .authorities(caps.stream().map(v->new SimpleGrantedAuthority("SCOPE_"+v)).toArray(SimpleGrantedAuthority[]::new));
    }
}
