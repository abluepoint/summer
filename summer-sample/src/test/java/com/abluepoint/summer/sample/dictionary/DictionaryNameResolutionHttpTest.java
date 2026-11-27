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
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DictionaryNameResolutionHttpTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate managedDb;
    @Autowired org.springframework.transaction.PlatformTransactionManager managedTransactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources managedInterfaces;
    private com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture managed;
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    private String operatorRole;

    @org.junit.jupiter.api.BeforeEach
    void currentManagedAuthorization() {
        managed = new com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture(
                managedDb, managedTransactions, managedInterfaces);
        managed.configuration("ent-acme", "dictionaries", true, "dictionary-operator");
        operatorRole = current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), "operator",
                (handler, path, method) -> handler.getBeanType().equals(DictionaryController.class)
                        && handler.getMethod().getName().equals("draft") && method.equals("GET")
                        && path.equals("/api/dictionaries/{code}/draft"));
        managed.data("ent-acme", "operator", "dictionaries", "read", "TENANT_ALL");
        managed.interfaces("PLATFORM:platform", "other-author", java.util.Set.of("dictionaries"));
        managed.data("ent-acme", "other-author", "dictionaries", "create", "TENANT_ALL");
        // Creating an item also advances the parent dictionary draft revision.
        managed.data("ent-acme", "other-author", "dictionaries", "update", "TENANT_ALL");
    }

    @org.junit.jupiter.api.AfterEach
    void releaseManagedAuthorization() {
        current.revoke(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), "operator", operatorRole);
        managed.close();
    }

    private static final String PUBLIC = "/public/enterprises/ent-acme/dictionaries/name-resolution";
    private static final String RESOLVE = "/api/v1/platform/dictionaries/name-resolution/resolve-names";
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
        http.perform(post(RESOLVE).header("X-Enterprise-Id","ent-other").with(identity("SCOPE_platform.dictionary.read"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"values\":[\"selected\"]}"))
                .andExpect(status().isOk());
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
    void enterpriseOverrideCannotChangePlatformNames() throws Exception {
        create();
        http.perform(post("/api/dictionaries/name-resolution/items").with(writer()).contentType(MediaType.APPLICATION_JSON)
                .content(DraftRevisionFixture.revised(http, "/api/dictionaries/name-resolution/items", "{\"value\":\"old-value\",\"label\":\"Base old name\",\"sortOrder\":1,\"enabled\":true}")))
                .andExpect(status().isOk());
        publish(1);
        http.perform(put("/api/dictionaries/name-resolution/overrides/_tenant").with(writer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":[{\"value\":\"selected\",\"label\":\"Override name\",\"sortOrder\":0,\"enabled\":true}],\"publicReadable\":true,\"enabled\":true}"))
                .andExpect(status().isNotFound());
        http.perform(post(RESOLVE).with(identity("SCOPE_platform.dictionary.read"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"values\":[\"selected\",\"old-value\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].label").value("Selected"))
                .andExpect(jsonPath("$.data.items[1].label").value("Base old name"))
                .andExpect(jsonPath("$.data.publishedVersion").value(2))
                .andExpect(jsonPath("$.data.publishedRevision").isEmpty())
                .andExpect(jsonPath("$.data.source").value("base"));
        http.perform(get(PUBLIC)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void invisibleItemsHaveExactlyTheSameUnknownShapeAsAbsentValues() throws Exception {
        create();
        var other = jwt().jwt(j -> j.claim("scope", "platform.dictionary.write").subject("other-author").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.write"));
        http.perform(post("/api/dictionaries/name-resolution/items").with(other).contentType(MediaType.APPLICATION_JSON)
                .content(DraftRevisionFixture.revised(http, "/api/dictionaries/name-resolution/items", "{\"value\":\"hidden\",\"label\":\"Secret name\",\"sortOrder\":1,\"enabled\":true}")))
                .andExpect(status().isOk());
        publish(1);
        // Current DATA scope replaces the retired domain-level data-scopes write API.
        managed.revokePublicRead("ent-acme", "dictionaries");
        managed.data("ent-acme", "dictionary-operator", "dictionaries", "read", "SELF");
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
        return jwt().jwt(token -> token.claim("scope", authority.replaceFirst("^SCOPE_", "")).subject(subject)
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new SimpleGrantedAuthority(authority));
    }
}
