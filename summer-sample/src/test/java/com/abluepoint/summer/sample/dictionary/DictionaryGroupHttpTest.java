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
class DictionaryGroupHttpTest {
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
}
