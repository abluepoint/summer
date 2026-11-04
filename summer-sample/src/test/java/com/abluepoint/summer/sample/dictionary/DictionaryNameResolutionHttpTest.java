package com.abluepoint.summer.sample.dictionary;

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

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DictionaryNameResolutionHttpTest {
    private static final String PUBLIC = "/public/enterprises/ent-acme/dictionaries/name-resolution";
    private static final String RESOLVE = "/api/v1/enterprises/ent-acme/dictionaries/name-resolution/resolve-names";
    @Autowired MockMvc http;

    @Test
    void namesIncludeDisabledValuesButOldValidationStillRejectsThem() throws Exception {
        create();
        http.perform(patch("/api/dictionaries/name-resolution/items/selected").with(writer())
                .contentType(MediaType.APPLICATION_JSON).content(DraftRevisionFixture.revised(http,
                        "/api/dictionaries/name-resolution/items/selected", "{\"label\":\"Current name\",\"description\":\"Published explanation\",\"sortOrder\":0,\"enabled\":false}")))
                .andExpect(status().isOk());
        publish(1);
        http.perform(post(RESOLVE).with(identity("SCOPE_platform.dictionary.read"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"values\":[\"selected\",\"01\",\"1\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].label").value("Current name"))
                .andExpect(jsonPath("$.data.items[0].description").value("Published explanation"))
                .andExpect(jsonPath("$.data.items[0].status").value("disabled"))
                .andExpect(jsonPath("$.data.items[1].value").value("01"))
                .andExpect(jsonPath("$.data.items[1].status").value("unknown"))
                .andExpect(jsonPath("$.data.items[2].value").value("1"))
                .andExpect(jsonPath("$.data.publishedVersion").value(2))
                .andExpect(jsonPath("$.data.effectiveRevision").value(2))
                .andExpect(jsonPath("$.data.source").value("base"))
                .andExpect(jsonPath("$.data.effectiveToken").isString());
        http.perform(get(PUBLIC + "/items/selected")).andExpect(status().isNotFound());
    }
    @Test
    void dictionaryLevelDenialAndInvalidValuesAreNeverSuccessfulUnknowns() throws Exception {
        create();
        for (String values : List.of("[1]", "[true]", "[null]", "[]")) {
            http.perform(post(RESOLVE).with(identity("SCOPE_platform.dictionary.read"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"values\":" + values + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        }
        http.perform(post(RESOLVE).with(identity("dictionary-reader", "SCOPE_customer.read"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"values\":[\"selected\"]}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
        http.perform(post(RESOLVE.replace("ent-acme", "ent-other")).with(identity("SCOPE_platform.dictionary.read"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"values\":[\"selected\"]}"))
                .andExpect(status().isForbidden());
        long version = 1;
        for (String flag : List.of("publicReadable", "enabled")) {
            changeFlag(flag, false); publish(version++);
            http.perform(post(RESOLVE).with(identity("SCOPE_platform.dictionary.read"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"values\":[\"selected\",\"missing\"]}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
            changeFlag(flag, true); publish(version++);
        }
    }

    @Test
    void overrideLabelsAndBaseOnlyOldValuesUseTheSameAuthorizedSource() throws Exception {
        create();
        http.perform(post("/api/dictionaries/name-resolution/items").with(writer()).contentType(MediaType.APPLICATION_JSON)
                .content(DraftRevisionFixture.revised(http, "/api/dictionaries/name-resolution/items", "{\"value\":\"old-value\",\"label\":\"Base old name\",\"sortOrder\":1,\"enabled\":true}")))
                .andExpect(status().isOk());
        publish(1);
        http.perform(put("/api/dictionaries/name-resolution/overrides/_tenant").with(writer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":[{\"value\":\"selected\",\"label\":\"Override name\",\"sortOrder\":0,\"enabled\":true}],\"publicReadable\":true,\"enabled\":true}"))
                .andExpect(status().isOk());
        http.perform(post(RESOLVE).with(identity("SCOPE_platform.dictionary.read"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"values\":[\"selected\",\"old-value\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].label").value("Override name"))
                .andExpect(jsonPath("$.data.items[1].label").value("Base old name"))
                .andExpect(jsonPath("$.data.publishedVersion").value(2))
                .andExpect(jsonPath("$.data.publishedRevision").isNumber())
                .andExpect(jsonPath("$.data.source").value("override"));
        http.perform(get(PUBLIC)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void invisibleItemsHaveExactlyTheSameUnknownShapeAsAbsentValues() throws Exception {
        create();
        var other = jwt().jwt(j -> j.subject("other-author").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.write"));
        http.perform(post("/api/dictionaries/name-resolution/items").with(other).contentType(MediaType.APPLICATION_JSON)
                .content(DraftRevisionFixture.revised(http, "/api/dictionaries/name-resolution/items", "{\"value\":\"hidden\",\"label\":\"Secret name\",\"sortOrder\":1,\"enabled\":true}")))
                .andExpect(status().isOk());
        publish(1);
        var policyWriter = jwt().jwt(j -> j.subject("dictionary-operator")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.data-scope.write"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.data-scope.write"));
        http.perform(put("/api/v1/enterprises/ent-acme/data-scopes/dictionaries").with(policyWriter)
                .contentType(MediaType.APPLICATION_JSON).content("{\"rules\":[{\"type\":\"SELF\"}]}"))
                .andExpect(status().isOk());
        http.perform(post(RESOLVE).with(identity("SCOPE_platform.dictionary.read"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"values\":[\"hidden\",\"absent\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].status").value("unknown"))
                .andExpect(jsonPath("$.data.items[0].label").isEmpty())
                .andExpect(jsonPath("$.data.items[0].description").value(""))
                .andExpect(jsonPath("$.data.items[1].status").value("unknown"))
                .andExpect(jsonPath("$.data.items[1].label").isEmpty());
    }

    private void create() throws Exception {
        http.perform(post("/api/dictionaries").with(writer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"name-resolution\",\"name\":\"Selected item\",\"published\":false,\"publicReadable\":true,\"enabled\":true}"))
                .andExpect(status().isOk());
        http.perform(post("/api/dictionaries/name-resolution/items").with(writer()).contentType(MediaType.APPLICATION_JSON)
                .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(http, "/api/dictionaries/name-resolution/items", "{\"value\":\"selected\",\"label\":\"Selected\",\"sortOrder\":0,\"enabled\":true}")))
                .andExpect(status().isOk());
        publish(0);
    }

    private void publish(long version) throws Exception {
        http.perform(post("/api/dictionaries/name-resolution/publish").with(writer())
                .contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(http, "/api/dictionaries/name-resolution/publish", "{\"expectedVersion\":" + version + "}")))
                .andExpect(status().isOk());
    }

    private void changeFlag(String flag, boolean value) throws Exception {
        http.perform(patch("/api/dictionaries/name-resolution").with(writer()).contentType(MediaType.APPLICATION_JSON)
                .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(http, "/api/dictionaries/name-resolution", "{\"name\":\"Selected item\",\"" + flag + "\":" + value + "}")))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor writer() { return identity("SCOPE_platform.dictionary.write"); }
    private static RequestPostProcessor identity(String authority) {
        return identity("dictionary-operator", authority);
    }
    private static RequestPostProcessor identity(String subject, String authority) {
        return jwt().jwt(token -> token.subject(subject)
                .claim("summer_managed_grants", !"dictionary-operator".equals(subject))
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new SimpleGrantedAuthority(authority));
    }
}
