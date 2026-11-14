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
class DictionarySelectedItemHttpTest {
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
        operatorRole = current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"), "operator",
                (handler, path, method) -> handler.getBeanType().equals(DictionaryController.class)
                        && handler.getMethod().getName().equals("draft") && method.equals("GET")
                        && path.equals("/api/dictionaries/{code}/draft"));
        managed.data("ent-acme", "operator", "dictionaries", "read", "TENANT_ALL");
    }

    @org.junit.jupiter.api.AfterEach
    void releaseManagedAuthorization() {
        current.revoke(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"), "operator", operatorRole);
        managed.close();
    }

    private static final String PUBLIC = "/public/enterprises/ent-acme/dictionaries/selected-item";
    @Autowired MockMvc http;

    @Test
    void aPreviouslySelectedItemBecomesUnavailableWithoutBreakingTheOptionListOrManagement() throws Exception {
        create();
        http.perform(get(PUBLIC + "/items/selected")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("selected"));
        http.perform(patch("/api/dictionaries/selected-item/items/selected").with(writer())
                .contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(http, "/api/dictionaries/selected-item/items/selected", "{\"label\":\"Selected\",\"sortOrder\":0,\"enabled\":false}")))
                .andExpect(status().isOk());
        publish(1);
        http.perform(get(PUBLIC)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", org.hamcrest.Matchers.hasSize(0)));
        http.perform(get(PUBLIC + "/items/selected").header("X-Request-Id", "selected-disabled"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SUMMER_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").value("selected-disabled"));
        http.perform(get("/api/dictionaries").with(identity("SCOPE_platform.dictionary.read"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].items[0].enabled").value(false));
    }

    @Test
    void malformedAndUnknownValuesUseTheExistingUnifiedErrors() throws Exception {
        create();
        http.perform(get(PUBLIC + "/items/INVALID").header("X-Request-Id", "selected-invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("SUMMER_DICTIONARY_INVALID"))
                .andExpect(jsonPath("$.error.violations[0].field").value("value"))
                .andExpect(jsonPath("$.requestId").value("selected-invalid"));
        http.perform(get(PUBLIC + "/items/unknown")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUMMER_NOT_FOUND"));
    }

    @Test
    void consumptionUsesTheExistingReadPermissionAndTenantBoundary() throws Exception {
        create();
        http.perform(get(PUBLIC + "/items/selected").with(identity("dictionary-reader", "SCOPE_customer.read")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
        http.perform(get("/public/enterprises/ent-other/dictionaries/selected-item/items/selected"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void privateUnpublishedAndDisabledTypesCannotExposeASelectedItem() throws Exception {
        create();
        long currentVersion = 1;
        for (String flag : List.of("publicReadable", "enabled")) {
            changeFlag(flag, false);
            publish(currentVersion++);
            http.perform(get(PUBLIC + "/items/selected")).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("SUMMER_NOT_FOUND"));
            changeFlag(flag, true);
            publish(currentVersion++);
        }
    }

    @Test
    void selectedItemComesFromThePublishedSnapshotInsteadOfTheLatestDraft() throws Exception {
        create();
        http.perform(post("/api/dictionaries/selected-item/publish").with(writer())
                .contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(http, "/api/dictionaries/selected-item/publish", "{\"expectedVersion\":1}")))
                .andExpect(status().isOk());
        http.perform(post("/api/dictionaries/selected-item/items").with(writer())
                .contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(http, "/api/dictionaries/selected-item/items", "{\"value\":\"new-draft\",\"label\":\"Draft\",\"sortOrder\":1,\"enabled\":true}")))
                .andExpect(status().isOk());
        http.perform(get(PUBLIC + "/items/selected")).andExpect(status().isOk());
        http.perform(get(PUBLIC + "/items/new-draft")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUMMER_NOT_FOUND"));
    }

    private void create() throws Exception {
        http.perform(post("/api/dictionaries").with(writer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"selected-item\",\"name\":\"Selected item\",\"published\":false,\"publicReadable\":true,\"enabled\":true}"))
                .andExpect(status().isOk());
        http.perform(post("/api/dictionaries/selected-item/items").with(writer()).contentType(MediaType.APPLICATION_JSON)
                .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(http, "/api/dictionaries/selected-item/items", "{\"value\":\"selected\",\"label\":\"Selected\",\"sortOrder\":0,\"enabled\":true}")))
                .andExpect(status().isOk());
        publish(0);
    }

    private void publish(long version) throws Exception {
        http.perform(post("/api/dictionaries/selected-item/publish").with(writer())
                .contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(http, "/api/dictionaries/selected-item/publish", "{\"expectedVersion\":" + version + "}")))
                .andExpect(status().isOk());
    }

    private void changeFlag(String flag, boolean value) throws Exception {
        http.perform(patch("/api/dictionaries/selected-item").with(writer()).contentType(MediaType.APPLICATION_JSON)
                .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(http, "/api/dictionaries/selected-item", "{\"name\":\"Selected item\",\"" + flag + "\":" + value + "}")))
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
