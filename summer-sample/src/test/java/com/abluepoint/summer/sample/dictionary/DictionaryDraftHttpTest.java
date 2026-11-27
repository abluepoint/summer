package com.abluepoint.summer.sample.dictionary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext
class DictionaryDraftHttpTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate managedDb;
    @Autowired org.springframework.transaction.PlatformTransactionManager managedTransactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources managedInterfaces;
    private com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture managed;

    @org.junit.jupiter.api.BeforeEach
    void currentManagedAuthorization() {
        managed = new com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture(
                managedDb, managedTransactions, managedInterfaces);
        managed.configuration("ent-acme", "dictionaries", false, "operator");
        managed.interfaces("PLATFORM:platform", "creator", java.util.Set.of("dictionaries"));
        managed.data("ent-acme", "creator", "dictionaries", "read", "TENANT_ALL");
        managed.data("ent-acme", "creator", "dictionaries", "create", "TENANT_ALL");
        managed.data("ent-acme", "creator", "dictionaries", "update", "TENANT_ALL");
    }

    @org.junit.jupiter.api.AfterEach
    void releaseManagedAuthorization() { managed.close(); }

    @Autowired MockMvc http;
    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().jwt(j -> j.claim("scope", "platform.dictionary.read platform.dictionary.write").subject("operator").claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId","ent-acme","status","ACTIVE"))))
            .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.read"), new SimpleGrantedAuthority("SCOPE_platform.dictionary.write"));
    }
    @Test void savesStringsAndDescriptionButRejectsStaleAndUnconditionalWrites() throws Exception {
        http.perform(post("/api/dictionaries").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"draft-http\",\"name\":\"Before\"}")).andExpect(status().isOk());
        String body = """
            {"expectedDraftRevision":0,"name":"After","publicReadable":true,"enabled":true,
             "items":[{"id":"new:one","value":"1","label":"One","description":"Explanation","enabled":true},{"id":"new:zero","value":"01","label":"Zero One","enabled":true}],"itemOrder":["new:zero","new:one"]}
            """;
        http.perform(put("/api/dictionaries/draft-http/draft").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.draftRevision").value(1)).andExpect(jsonPath("$.data.items[0].value").value("01")).andExpect(jsonPath("$.data.items[0].description").value(""));
        http.perform(put("/api/dictionaries/draft-http/draft").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body.replace("After","Stale")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.requestId").isString());
        http.perform(get("/api/dictionaries/draft-http/draft").with(admin())).andExpect(jsonPath("$.data.name").value("After"));
        http.perform(patch("/api/dictionaries/draft-http").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Unsafe\"}"))
            .andExpect(status().isBadRequest());
    }
    @Test void noDictionaryScopeCannotUseIdenticalSaveAsReadOrWrite() throws Exception {
        http.perform(put("/api/dictionaries/draft-http/draft").with(jwt()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedDraftRevision\":1,\"name\":\"After\",\"items\":[],\"itemOrder\":[],\"enabled\":true,\"publicReadable\":true}"))
            .andExpect(status().isForbidden());
    }
    @Test void createOnlyAppendDoesNotRequireUpdateForSparseStoredPositions() throws Exception {
        String path = "/api/dictionaries/sparse-http";
        http.perform(post("/api/dictionaries").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"sparse-http\",\"name\":\"Sparse\"}")).andExpect(status().isOk());
        http.perform(post(path + "/items").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedDraftRevision\":0,\"value\":\"a\",\"label\":\"A\",\"sortOrder\":10}")).andExpect(status().isOk());
        http.perform(post(path + "/items").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedDraftRevision\":1,\"value\":\"b\",\"label\":\"B\",\"sortOrder\":20}")).andExpect(status().isOk());
        String raw = http.perform(get(path + "/draft").with(admin())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String a = com.jayway.jsonpath.JsonPath.read(raw,"$.data.items[0].id");
        String b = com.jayway.jsonpath.JsonPath.read(raw,"$.data.items[1].id");
        String body = """
            {"expectedDraftRevision":2,"name":"Sparse","publicReadable":false,"enabled":true,
             "items":[{"id":"%s","value":"a","label":"A","enabled":true},{"id":"%s","value":"b","label":"B","enabled":true},{"id":"new:c","value":"c","label":"C","enabled":true}],"itemOrder":["%s","%s","new:c"]}
            """.formatted(a,b,a,b);
        http.perform(put(path + "/draft").with(jwt().jwt(j -> j.claim("scope", "platform.dictionary.read platform.dictionary.create").subject("creator").claim("enterprise_memberships",java.util.List.of(java.util.Map.of("enterpriseId","ent-acme","status","ACTIVE"))))
            .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.read"), new SimpleGrantedAuthority("SCOPE_platform.dictionary.create")))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[2].value").value("c"));
    }
    @Test void editableSparseRanksPersistAndPublishWithoutChangingGroupOrder() throws Exception {
        String path = "/api/dictionaries/item-ranks";
        var mapper = new tools.jackson.databind.ObjectMapper();
        http.perform(post("/api/dictionaries").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"item-ranks\",\"name\":\"Item ranks\"}")).andExpect(status().isOk());
        String raw = http.perform(put(path + "/draft").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
            {"expectedDraftRevision":0,"name":"Item ranks","publicReadable":false,"enabled":true,
             "items":[{"id":"new:a","value":"a","label":"A","enabled":true,"sortOrder":10},{"id":"new:b","value":"b","label":"B","enabled":true,"sortOrder":20},{"id":"new:c","value":"c","label":"C","enabled":true,"sortOrder":50}],
             "itemOrder":["new:a","new:b","new:c"],"groups":[{"id":"new:first","code":"first","name":"First","enabled":true,
             "memberIds":["new:a","new:b"],"memberOrder":["new:a","new:b"],"memberSortOrders":{"new:a":5,"new:b":8}}]}
            """)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[*].sortOrder").value(org.hamcrest.Matchers.contains(10,20,50)))
            .andReturn().getResponse().getContentAsString();
        java.util.Map<String,Object> view = com.jayway.jsonpath.JsonPath.read(raw,"$.data");
        var request = new java.util.LinkedHashMap<String,Object>();
        for (String key : java.util.List.of("name","publicReadable","enabled","items","itemOrder")) request.put(key,view.get(key));
        request.put("expectedDraftRevision",1);
        java.util.List<java.util.Map<String,Object>> values = com.jayway.jsonpath.JsonPath.read(raw,"$.data.items");
        request.put("items",values);
        java.util.Map<String,Object> originalGroup = com.jayway.jsonpath.JsonPath.read(raw,"$.data.groups[0]");
        // Omitting groups preserves all existing references and their independent ranks.
        for (Object invalid : java.util.List.of(-1,2147483648L,new java.math.BigDecimal("1.5"),"bad")) {
            values.getFirst().put("sortOrder",invalid);
            http.perform(put(path + "/draft").with(admin()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
        values.getFirst().put("sortOrder",15);
        http.perform(put(path + "/draft").with(jwt().jwt(j -> j.claim("scope","platform.dictionary.read platform.dictionary.create").subject("creator")
                .claim("enterprise_memberships",java.util.List.of(java.util.Map.of("enterpriseId","ent-acme","status","ACTIVE"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.read"),new SimpleGrantedAuthority("SCOPE_platform.dictionary.create")))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request))).andExpect(status().isForbidden());
        http.perform(put(path + "/draft").with(admin()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.draftRevision").value(2));
        http.perform(get(path + "/draft").with(admin())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[*].sortOrder").value(org.hamcrest.Matchers.contains(15,20,50)))
            .andExpect(jsonPath("$.data.groups[0]").value(org.hamcrest.Matchers.equalTo(originalGroup)));
        http.perform(post(path + "/publish").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content(DraftRevisionFixture.lifecycle(http,path + "/publish","{\"expectedVersion\":0}"))).andExpect(status().isOk());
        request.put("expectedDraftRevision",2); values.getFirst().put("sortOrder",1000);
        http.perform(put(path + "/draft").with(admin()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[*].value").value(org.hamcrest.Matchers.contains("b","c","a")))
            .andExpect(jsonPath("$.data.items[*].sortOrder").value(org.hamcrest.Matchers.contains(20,50,1000)))
            .andExpect(jsonPath("$.data.groups[0].memberIds").value(org.hamcrest.Matchers.equalTo(originalGroup.get("memberIds"))))
            .andExpect(jsonPath("$.data.groups[0].memberSortOrders").value(org.hamcrest.Matchers.equalTo(originalGroup.get("memberSortOrders"))));
        http.perform(post(path + "/publish").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content(DraftRevisionFixture.lifecycle(http,path + "/publish","{\"expectedVersion\":1}"))).andExpect(status().isOk());
        http.perform(get(path + "/effective").with(admin())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[*].sortOrder").value(org.hamcrest.Matchers.contains(20,50,1000)));
        http.perform(get(path + "/versions/1").with(admin())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.snapshot.items[*].sortOrder").value(org.hamcrest.Matchers.contains(15,20,50)));
        http.perform(post(path + "/rollback").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content(DraftRevisionFixture.lifecycle(http,path + "/rollback","{\"expectedVersion\":2,\"targetVersion\":1}"))).andExpect(status().isOk());
        http.perform(get(path + "/effective").with(admin())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[*].sortOrder").value(org.hamcrest.Matchers.contains(15,20,50)));
    }
}
