package com.abluepoint.summer.sample.dictionary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class DictionarySelectionHttpTest {
    @Autowired MockMvc http;
    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().jwt(j -> j.claim("scope", "platform.dictionary.read platform.dictionary.write").subject("operator").claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId","ent-acme","status","ACTIVE"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.read"), new SimpleGrantedAuthority("SCOPE_platform.dictionary.write"));
    }
    private String published(String code) throws Exception {
        String path = "/api/dictionaries/" + code;
        http.perform(post("/api/dictionaries").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Selection\"}")).andExpect(status().isOk());
        http.perform(put(path + "/draft").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
            {"expectedDraftRevision":0,"name":"Selection","publicReadable":true,"enabled":true,
             "items":[{"id":"new:a","value":"01","label":"Leading zero","description":"Base description","enabled":true},
                      {"id":"new:b","value":"1","label":"One","enabled":true},
                      {"id":"new:c","value":"retired","label":"Old name","enabled":false}],
             "itemOrder":["new:a","new:b","new:c"],"groups":[
              {"id":"new:g","code":"empty","name":"Empty","enabled":true,"memberIds":[],"memberOrder":[]},
              {"id":"new:h","code":"closed","name":"Closed","enabled":false,"memberIds":["new:a"],"memberOrder":["new:a"]}]}
            """)).andExpect(status().isOk());
        http.perform(post(path + "/publish").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedDraftRevision\":1,\"expectedVersion\":0,\"expectedEffectiveRevision\":0}")).andExpect(status().isOk());
        return path;
    }
    @Test void choicesKeepExactStringsAndRetiredNamesWithoutChangingNameValidation() throws Exception {
        String path = published("selection-strings");
        http.perform(post(path + "/resolve-selection").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\":[\"retired\",\"missing\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.choices[0].value").value("01"))
                .andExpect(jsonPath("$.data.choices[1].value").value("1"))
                .andExpect(jsonPath("$.data.choices.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].label").value("Old name"))
                .andExpect(jsonPath("$.data.items[0].status").value("disabled"))
                .andExpect(jsonPath("$.data.items[1].status").value("unknown"));
        http.perform(post(path + "/resolve-selection").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"values\":[]}"))
                .andExpect(status().isOk());
        http.perform(post(path + "/resolve-names").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"values\":[]}"))
                .andExpect(status().isBadRequest());
        http.perform(post(path + "/resolve-selection").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"values\":[1]}"))
                .andExpect(status().isBadRequest());
    }
    @Test void unavailableGroupAndAuthorizedEmptyGroupAreDifferent() throws Exception {
        String path = published("selection-groups");
        http.perform(post(path + "/resolve-selection").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{\"values\":[],\"groupCode\":\"empty\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.groupStatus").value("available"))
                .andExpect(jsonPath("$.data.choices.length()").value(0));
        for (String group : java.util.List.of("missing", "closed")) {
            http.perform(post(path + "/resolve-selection").with(admin()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"values\":[],\"groupCode\":\"" + group + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.groupStatus").value("unavailable"))
                    .andExpect(jsonPath("$.data.choices.length()").value(0));
        }
    }
    @Test void oldOverrideArraysSupplyNamesButKeepBaseDescriptionsAndPublishedIdentity() throws Exception {
        String path = published("selection-overlay");
        String raw = http.perform(put(path + "/overrides/_tenant").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"value":[{"value":"01","label":"Overlay name","sortOrder":0,"enabled":true}],"publicReadable":true,"enabled":true}
                    """)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Number version = com.jayway.jsonpath.JsonPath.read(raw, "$.data.version");
        Number revision = com.jayway.jsonpath.JsonPath.read(raw, "$.data.publishedRevision");
        http.perform(post(path + "/overrides/_tenant/draft").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":" + version + ",\"value\":[{\"value\":\"1\",\"label\":\"Draft name\",\"sortOrder\":0,\"enabled\":true}],\"publicReadable\":true,\"enabled\":true}"))
                .andExpect(status().isOk());
        http.perform(post(path + "/resolve-selection").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\":[\"01\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.publishedRevision").value(revision.intValue()))
                .andExpect(jsonPath("$.data.choices[0].label").value("Overlay name"))
                .andExpect(jsonPath("$.data.choices[0].description").value("Base description"))
                .andExpect(jsonPath("$.data.items[0].description").value("Base description"));
    }

}
