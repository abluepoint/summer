package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"summer.security.storage.keys=memory", "summer.sample.cache.enabled=false", "summer.sample.resilience.enabled=false"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DataScopeEnforcementHttpTest {
    @Autowired MockMvc mvc;
    @Autowired AuthorizationRoleRepository roleRepository;
    @Autowired AuthorizationUserBindingRepository userRepository;
    @Autowired AuthorizationCapabilityRepository capabilityRepository;

    @Test
    void denyRuleWinsForPreviewSqlListRepositoryDetailAndWrite() throws Exception {
        create("scope.alice", "alice", "org-engineering");
        policy("system-parameters", """
            {"operator":"OR","rules":[{"type":"TENANT"},{"field":"CREATOR","operator":"EQ","value":"alice","effect":"DENY"}]}
            """);
        mvc.perform(get("/api/system-parameters").with(member("alice"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/system-parameters/scope.alice").with(member("alice"))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/system-parameters/scope.alice").with(member("alice"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"blocked\",\"version\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/enterprises/ent-acme/data-scopes/system-parameters:preview").with(member("admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"alice\",\"creatorId\":\"alice\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowed").value(false));
        mvc.perform(post("/api/system-parameters").with(member("alice")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"scope.denied\",\"type\":\"STRING\",\"value\":\"v\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void nestedValuesAndComparatorsHaveIdenticalPreviewListAndDetailResults() throws Exception {
        create("scope.allowed", "alice", "org-engineering");
        create("scope.wrong.org", "alice", "org-sales");
        create("scope.wrong.creator", "bob", "org-engineering");
        policy("system-parameters", """
            {"operator":"AND","conditions":{"operator":"AND","children":[
              {"field":"ORGANIZATION","operator":"IN","value":["org-engineering"]},
              {"operator":"OR","children":[{"field":"CREATOR","operator":"EQ","value":"alice"},
                {"field":"OWNER","operator":"EQ","value":"carol"}]},
              {"field":"CREATOR","operator":"NOT_IN","value":["bob"]}]}}
            """);
        mvc.perform(get("/api/system-parameters?size=1").with(member("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].key").value("scope.allowed"));
        mvc.perform(get("/api/system-parameters/scope.wrong.org").with(member("alice"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/system-parameters/scope.allowed").with(member("alice"))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/enterprises/ent-acme/data-scopes/system-parameters:preview").with(member("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"alice\",\"organizationId\":\"org-sales\",\"creatorId\":\"alice\",\"ownerId\":\"alice\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowed").value(false));
    }

    @Test
    void dictionaryWritesCannotBypassAnItemScopeWhenParentRemainsVisible() throws Exception {
        mvc.perform(post("/api/dictionaries").with(member("alice")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"scope-dictionary\",\"name\":\"Scope\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/dictionaries/scope-dictionary/items").with(member("bob")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"private\",\"label\":\"Private\",\"sortOrder\":1}"))
                .andExpect(status().isOk());
        policy("dictionaries", "{\"rules\":[{\"field\":\"CREATOR\",\"operator\":\"NE\",\"value\":\"bob\"}]}");
        mvc.perform(get("/api/dictionaries").with(member("alice"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'scope-dictionary')].items[0]").doesNotExist());
        mvc.perform(patch("/api/dictionaries/scope-dictionary/items/private").with(member("alice"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Not allowed\",\"sortOrder\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nestedDenialKeepsItsBranchConditionsAndOverridesMatchingOrAllows() throws Exception {
        create("branch.allowed", "alice", "org-engineering");
        create("branch.denied", "bob", "org-engineering");
        create("branch.other", "bob", "org-sales");
        policy("system-parameters", """
            {"conditions":{"operator":"OR","children":[
              {"operator":"AND","children":[{"field":"ORGANIZATION","value":"org-engineering"},
                {"field":"CREATOR","value":"bob","effect":"DENY"}]},
              {"field":"ORGANIZATION","value":"org-sales"},
              {"field":"CREATOR","value":"bob"}]}}
            """);
        mvc.perform(get("/api/system-parameters").with(member("alice"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/system-parameters/branch.denied").with(member("alice"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/system-parameters/branch.other").with(member("alice"))).andExpect(status().isOk());
    }

    @Test
    void organizationTreeIncludesDescendantsAndKeepsUnrelatedRowsOutsideTheQuery() throws Exception {
        create("tree.root", "alice", "org-acme");
        create("tree.child", "bob", "org-engineering");
        create("tree.unrelated", "bob", "org-unrelated");
        policy("system-parameters", """
            {"conditions":{"field":"ORGANIZATION_TREE","operator":"IN","value":["org-acme"]}}
            """);
        mvc.perform(get("/api/system-parameters").with(member("alice"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/system-parameters/tree.child").with(member("alice"))).andExpect(status().isOk());
        mvc.perform(get("/api/system-parameters/tree.unrelated").with(member("alice"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/enterprises/ent-acme/data-scopes/system-parameters:preview").with(member("admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"alice\",\"organizationId\":\"org-engineering\",\"creatorId\":\"bob\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowed").value(true));
    }

    @Test
    void orCannotEscapeTheTenantBoundaryAndPolicyValuesAreNeverSql() throws Exception {
        create("scope.boundary", "alice", "org-engineering");
        policy("system-parameters", """
            {"operator":"OR","rules":[{"field":"TENANT","value":"ent-other"},
             {"field":"CREATOR","value":"alice' OR 1=1 --"}]}
            """);
        mvc.perform(get("/api/system-parameters").with(member("alice"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/system-parameters/scope.boundary").with(member("alice"))).andExpect(status().isNotFound());
    }

    @Test
    void malformedTreesAndUnsupportedComparatorsFailBeforePolicyReplacement() throws Exception {
        for (String invalid : List.of(
                "{\"conditions\":{\"operator\":\"AND\",\"children\":[]}}",
                "{\"conditions\":{\"field\":\"CREATOR\",\"operator\":\"MATCHES\",\"value\":\".*\"}}",
                "{\"conditions\":{\"field\":\"CREATOR\",\"operator\":\"IN\",\"value\":[]}}",
                "{\"rules\":[{\"type\":\"TENANT\",\"effect\":\"IGNORE\"}]}")) {
            mvc.perform(put("/api/v1/enterprises/ent-acme/data-scopes/system-parameters").with(member("admin"))
                    .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("SUMMER_DATA_SCOPE_INVALID"));
        }
    }

    @Test
    void persistedUserDenialIsUsedByPreviewAndRealQueriesAndCannotBeSpoofed() throws Exception {
        create("binding.alice", "alice", "org-engineering");
        mvc.perform(put("/api/v1/enterprises/ent-acme/data-scopes/system-parameters/bindings/USER/alice")
                .with(member("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rules\":[{\"type\":\"TENANT\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/system-parameters").with(member("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/system-parameters/binding.alice").with(member("alice")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/enterprises/ent-acme/data-scopes/system-parameters:preview")
                .with(member("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"alice\",\"userDenies\":[],\"roleAllows\":[\"TENANT\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/enterprises/ent-acme/data-scopes/system-parameters:preview")
                .with(member("admin")).contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"alice\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reasonCode").value("USER_DENY"));
    }

    @Test
    void persistedRoleRangesAreIntersectedWithPolicyAndUserDenialsWin() throws Exception {
        create("roles.alice", "alice", "org-engineering");
        create("roles.bob", "bob", "org-sales");
        capabilityRepository.saveAndFlush(new AuthorizationCapabilityEntity("cap-scope-test", "ent-acme", "ENTERPRISE",
                "system.parameter.read", "system.parameter.read", "read", "LOW", "ACTIVE", 1));
        roleRepository.saveAndFlush(new AuthorizationRoleEntity("role-scope-test", "ent-acme", "Scoped", "scoped", false,
                "ACTIVE", "cap-scope-test", "system.parameter.read", null, 1));
        userRepository.saveAndFlush(new AuthorizationUserBindingEntity("binding-scope-test", "ent-acme", "alice", "role-scope-test", "", 1));
        mvc.perform(put("/api/v1/enterprises/ent-acme/data-scopes/system-parameters/bindings/ROLE/role-scope-test")
                .with(member("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rules\":[{\"type\":\"ORGANIZATION\",\"value\":\"org-engineering\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/system-parameters").with(member("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(put("/api/v1/enterprises/ent-acme/data-scopes/system-parameters/bindings/USER/alice")
                .with(member("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rules\":[{\"type\":\"TENANT\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/system-parameters").with(member("alice")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(post("/api/v1/enterprises/ent-acme/data-scopes/system-parameters:preview")
                .with(member("admin")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"alice\",\"organizationId\":\"org-engineering\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reasonCode").value("USER_DENY"));
    }

    private void policy(String resource, String body) throws Exception {
        mvc.perform(put("/api/v1/enterprises/ent-acme/data-scopes/" + resource).with(member("admin"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    private void create(String key, String subject, String organization) throws Exception {
        mvc.perform(post("/api/system-parameters").with(member(subject)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"" + key + "\",\"type\":\"STRING\",\"value\":\"v\",\"organizationId\":\"" + organization + "\"}"))
                .andExpect(status().isCreated());
    }

    private static RequestPostProcessor member(String subject) {
        List<String> capabilities = List.of("enterprise.data-scope.read", "enterprise.data-scope.write",
                "system.parameter.read", "system.parameter.write", "platform.dictionary.read", "platform.dictionary.write");
        return jwt().jwt(j -> j.subject(subject).claim("organization_id", "org-engineering")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of("ent-acme", capabilities)))
                .authorities(capabilities.stream().map(v -> new SimpleGrantedAuthority("SCOPE_" + v)).toArray(SimpleGrantedAuthority[]::new));
    }
}
