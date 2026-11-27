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
class DictionaryGroupHttpTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate managedDb;
    @Autowired org.springframework.transaction.PlatformTransactionManager managedTransactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources managedInterfaces;
    private com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture managed;

    @org.junit.jupiter.api.BeforeEach
    void currentManagedAuthorization() {
        managed = new com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture(
                managedDb, managedTransactions, managedInterfaces);
        managed.configuration("ent-acme", "dictionaries", false, "operator");
    }

    @org.junit.jupiter.api.AfterEach
    void releaseManagedAuthorization() { managed.close(); }

    @Autowired MockMvc http;
    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().jwt(j -> j.claim("scope", "platform.dictionary.read platform.dictionary.write").subject("operator").claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId","ent-acme","status","ACTIVE"))))
            .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.read"), new SimpleGrantedAuthority("SCOPE_platform.dictionary.write"));
    }
    @Test void overlappingGroupsHaveIndependentOrderAndShareTheDraftRevision() throws Exception {
        String path = "/api/dictionaries/group-http";
        http.perform(post("/api/dictionaries").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"group-http\",\"name\":\"Groups\"}")).andExpect(status().isOk());
        http.perform(put(path + "/draft").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
            {"expectedDraftRevision":0,"name":"Groups","publicReadable":true,"enabled":true,
             "items":[{"id":"new:a","value":"a","label":"A","enabled":true},{"id":"new:b","value":"b","label":"B","enabled":true}],
             "itemOrder":["new:a","new:b"],"groups":[
              {"id":"new:first","code":"first","name":"First","enabled":true,"memberIds":["new:b","new:a"],"memberOrder":["new:b","new:a"]},
              {"id":"new:second","code":"second","name":"Second","enabled":true,"memberIds":["new:a"],"memberOrder":["new:a"]}]}
            """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.draftRevision").value(1))
            .andExpect(jsonPath("$.data.groups.length()").value(2))
            .andExpect(jsonPath("$.data.groups[0].code").value("first"))
            .andExpect(jsonPath("$.data.groups[0].memberIds.length()").value(2));
    }
    @Test void groupOrderMustBeCompleteButExplicitMemberRemovalIsAllowed() throws Exception {
        String path = "/api/dictionaries/group-order-contract";
        http.perform(post("/api/dictionaries").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"group-order-contract\",\"name\":\"Order contract\"}")).andExpect(status().isOk());
        String raw = http.perform(put(path + "/draft").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
            {"expectedDraftRevision":0,"name":"Order contract","publicReadable":true,"enabled":true,
             "items":[{"id":"new:a","value":"a","label":"A","enabled":true},{"id":"new:b","value":"b","label":"B","enabled":true}],
             "itemOrder":["new:a","new:b"],"groups":[{"id":"new:first","code":"first","name":"First","enabled":true,"memberIds":["new:a","new:b"],"memberOrder":["new:a","new:b"]}]}
            """)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var mapper = new tools.jackson.databind.ObjectMapper();
        java.util.Map<String,Object> view = com.jayway.jsonpath.JsonPath.read(raw, "$.data");
        java.util.Map<String,Object> request = new java.util.LinkedHashMap<>();
        for (String key : java.util.List.of("name", "publicReadable", "enabled", "items", "itemOrder")) request.put(key, view.get(key));
        request.put("expectedDraftRevision", view.get("draftRevision"));
        java.util.List<java.util.Map<String,Object>> groups = com.jayway.jsonpath.JsonPath.read(raw, "$.data.groups");
        var group = new java.util.LinkedHashMap<>(groups.getFirst());
        group.remove("everPublished");
        java.util.List<String> memberIds = com.jayway.jsonpath.JsonPath.read(raw, "$.data.groups[0].memberIds");
        request.put("groups", java.util.List.of(group));
        for (var badOrder : java.util.List.of(java.util.List.of(memberIds.getFirst()),
                java.util.List.of(memberIds.getFirst(), memberIds.getFirst()), java.util.List.of(memberIds.getFirst(), "unknown"))) {
            group.put("memberOrder", badOrder);
            http.perform(put(path + "/draft").with(admin()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.requestId").isString());
        }
        group.remove("memberOrder");
        http.perform(put(path + "/draft").with(admin()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
        http.perform(get(path + "/draft").with(admin()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.draftRevision").value(1))
            .andExpect(jsonPath("$.data.groups[0].memberIds.length()").value(2));
        group.put("memberIds", java.util.List.of(memberIds.getFirst()));
        group.put("memberOrder", java.util.List.of(memberIds.getFirst()));
        http.perform(put(path + "/draft").with(admin()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.draftRevision").value(2))
            .andExpect(jsonPath("$.data.groups[0].memberIds.length()").value(1));
    }
    @Test void sparseRanksSurviveDraftPublicationHistoryAndRollback() throws Exception {
        String path = "/api/dictionaries/sparse-ranks";
        var mapper = new tools.jackson.databind.ObjectMapper();
        http.perform(post("/api/dictionaries").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"sparse-ranks\",\"name\":\"Sparse ranks\"}")).andExpect(status().isOk());
        String raw = http.perform(put(path + "/draft").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
            {"expectedDraftRevision":0,"name":"Sparse ranks","publicReadable":true,"enabled":true,
             "items":[{"id":"new:a","value":"a","label":"A","enabled":true},{"id":"new:b","value":"b","label":"B","enabled":true},{"id":"new:c","value":"c","label":"C","enabled":true}],
             "itemOrder":["new:a","new:b","new:c"],"groups":[{"id":"new:first","code":"first","name":"First","enabled":true,
             "memberIds":["new:a","new:b","new:c"],"memberOrder":["new:a","new:b","new:c"],"memberSortOrders":{"new:a":10,"new:b":20,"new:c":50}}]}
            """)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        java.util.Map<String,Object> view = com.jayway.jsonpath.JsonPath.read(raw,"$.data");
        java.util.List<String> ids = com.jayway.jsonpath.JsonPath.read(raw,"$.data.groups[0].memberIds");
        java.util.Map<String,Object> group = new java.util.LinkedHashMap<>(com.jayway.jsonpath.JsonPath.read(raw,"$.data.groups[0]"));
        group.remove("everPublished"); group.put("memberOrder",ids);
        var ranks = new java.util.LinkedHashMap<String,Object>();
        ranks.put(ids.get(0),10); ranks.put(ids.get(1),20); ranks.put(ids.get(2),50);
        group.put("memberSortOrders",ranks);
        var request = new java.util.LinkedHashMap<String,Object>();
        for (String key : java.util.List.of("name","publicReadable","enabled","items","itemOrder")) request.put(key,view.get(key));
        request.put("groups",java.util.List.of(group)); request.put("expectedDraftRevision",1);
        http.perform(get(path + "/draft").with(admin())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.groups[0].memberSortOrders").value(ranks));
        for (Object invalid : java.util.List.of(0,-1,2147483648L,new java.math.BigDecimal("1.5"),"bad")) {
            ranks.put(ids.getFirst(),invalid);
            http.perform(put(path + "/draft").with(admin()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
        ranks.put(ids.getFirst(),15); // Changing a number need not change relative order.
        http.perform(put(path + "/draft").with(admin()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.groups[0].memberSortOrders").value(ranks));
        http.perform(post(path + "/publish").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content(DraftRevisionFixture.lifecycle(http,path + "/publish","{\"expectedVersion\":0}"))).andExpect(status().isOk());
        var publishedRanks = java.util.Map.of("a",15,"b",20,"c",50);
        http.perform(get(path + "/effective").with(admin())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.groups[0].memberSortOrders").value(org.hamcrest.Matchers.equalTo(publishedRanks)));
        request.put("expectedDraftRevision",2); ranks.put(ids.getFirst(),1000);
        http.perform(put(path + "/draft").with(admin()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.groups[0].memberIds").value(org.hamcrest.Matchers.equalTo(java.util.List.of(ids.get(1),ids.get(2),ids.getFirst()))))
            .andExpect(jsonPath("$.data.groups[0].memberSortOrders").value(ranks));
        http.perform(post(path + "/publish").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content(DraftRevisionFixture.lifecycle(http,path + "/publish","{\"expectedVersion\":1}"))).andExpect(status().isOk());
        http.perform(get(path + "/versions/1").with(admin())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.snapshot.groups[0].memberSortOrders").value(org.hamcrest.Matchers.equalTo(publishedRanks)));
        http.perform(post(path + "/rollback").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content(DraftRevisionFixture.lifecycle(http,path + "/rollback","{\"expectedVersion\":2,\"targetVersion\":1}"))).andExpect(status().isOk());
        http.perform(get(path + "/effective").with(admin())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.groups[0].memberSortOrders").value(org.hamcrest.Matchers.equalTo(publishedRanks)));
    }

    @Test void legacySnapshotsRecoverTheirOriginalConsecutiveRanks() {
        var mapper = new tools.jackson.databind.ObjectMapper();
        var snapshot = mapper.readValue("""
            {"id":"legacy","code":"legacy","name":"Legacy","enabled":true,"memberValues":["b","a"]}
            """,DictionarySnapshotGroup.class);
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Map.of("b",1,"a",2),snapshot.memberSortOrders());
    }
}
