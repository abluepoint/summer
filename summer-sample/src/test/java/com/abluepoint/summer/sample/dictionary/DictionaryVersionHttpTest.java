package com.abluepoint.summer.sample.dictionary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "summer.sample.parameter-events.scheduling-enabled=false")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DictionaryVersionHttpTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate managedDb;
    @Autowired org.springframework.transaction.PlatformTransactionManager managedTransactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources managedInterfaces;
    private com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture managed;

    @org.junit.jupiter.api.BeforeEach
    void currentManagedAuthorization() {
        managed = new com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture(
                managedDb, managedTransactions, managedInterfaces);
        managed.configuration("ent-acme", "dictionaries", true, "operator");
    }

    @org.junit.jupiter.api.AfterEach
    void releaseManagedAuthorization() { managed.close(); }

    // These legacy fixtures now declare their enterprise; production has no implicit sample tenant.
    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwt() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))));
    }

    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;

    @Test
    void publishDisableAndRollbackExposeOnlyPublishedSnapshotAndWriteEvidence() throws Exception {
        createDictionary();
        addItem("standard", "Standard", true);
        addItem("draft", "Draft", true);

        http.perform(get("/public/enterprises/ent-acme/dictionaries/versioned-segment"))
                .andExpect(status().isNotFound());

        http.perform(post("/api/dictionaries/versioned-segment/publish")
                        .header("X-Request-Id", "publish-v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(http, "/api/dictionaries/versioned-segment/publish", "{\"expectedVersion\":0}"))
                        .with(writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        http.perform(get("/public/enterprises/ent-acme/dictionaries/versioned-segment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.items", org.hamcrest.Matchers.hasSize(2)));

        addItem("new-option", "New option", true);
        http.perform(get("/public/enterprises/ent-acme/dictionaries/versioned-segment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", org.hamcrest.Matchers.hasSize(2)));

        http.perform(post("/api/dictionaries/versioned-segment/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(http, "/api/dictionaries/versioned-segment/publish", "{\"expectedVersion\":1}"))
                        .with(writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        http.perform(post("/api/dictionaries/versioned-segment/disable")
                        .header("X-Request-Id", "disable-v2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(http, "/api/dictionaries/versioned-segment/disable", "{\"expectedVersion\":2}"))
                        .with(writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
        http.perform(get("/public/enterprises/ent-acme/dictionaries/versioned-segment"))
                .andExpect(status().isNotFound());

        http.perform(post("/api/dictionaries/versioned-segment/rollback")
                        .header("X-Request-Id", "rollback-v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(http, "/api/dictionaries/versioned-segment/rollback", "{\"expectedVersion\":2,\"targetVersion\":1}"))
                        .with(writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        http.perform(get("/public/enterprises/ent-acme/dictionaries/versioned-segment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", org.hamcrest.Matchers.hasSize(2)));

        assertThat(jdbc.queryForObject("select count(*) from summer_dictionary_version", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from summer_dictionary_outbox", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from summer_audit_event where resource = 'dictionary:versioned-segment'", Integer.class)).isEqualTo(8);
    }

    @Test
    void staleVersionAndIllegalRollbackAreRejectedAndWriterScopeIsRequired() throws Exception {
        createDictionary();
        addItem("standard", "Standard", true);
        http.perform(post("/api/dictionaries/versioned-segment/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(http, "/api/dictionaries/versioned-segment/publish", "{\"expectedVersion\":0}"))
                        .with(writer()))
                .andExpect(status().isOk());

        http.perform(post("/api/dictionaries/versioned-segment/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(http, "/api/dictionaries/versioned-segment/publish", "{\"expectedVersion\":0}"))
                        .with(writer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_DICTIONARY_VERSION_CONFLICT"));

        http.perform(post("/api/dictionaries/versioned-segment/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(http, "/api/dictionaries/versioned-segment/rollback", "{\"expectedVersion\":1,\"targetVersion\":9}"))
                        .with(writer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_DICTIONARY_INVALID_STATE"));

        http.perform(post("/api/dictionaries/versioned-segment/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(http, "/api/dictionaries/versioned-segment/disable", "{\"expectedVersion\":1}"))
                        .with(jwt().jwt(j -> j.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).claim("scope", "customer.read")).authorities(new SimpleGrantedAuthority("SCOPE_customer.read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void idempotencyKeyReplaysTheCommittedPublishWithoutCreatingAnotherVersion() throws Exception {
        createDictionary();
        addItem("standard", "Standard", true);
        String body = "{\"expectedVersion\":0,\"idempotencyKey\":\"publish-once\"}";
        http.perform(post("/api/dictionaries/versioned-segment/publish")
                        .contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(http, "/api/dictionaries/versioned-segment/publish", body)).with(writer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        http.perform(post("/api/dictionaries/versioned-segment/publish")
                        .contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(http, "/api/dictionaries/versioned-segment/publish", body)).with(writer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        http.perform(post("/api/dictionaries/versioned-segment/disable").with(writer())
                        .contentType(MediaType.APPLICATION_JSON).content(DraftRevisionFixture.lifecycle(http,
                                "/api/dictionaries/versioned-segment/disable", "{\"expectedVersion\":1}")))
                .andExpect(status().isOk());
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/dictionaries/versioned-segment").with(writer())
                        .contentType(MediaType.APPLICATION_JSON).content(DraftRevisionFixture.revised(http,
                                "/api/dictionaries/versioned-segment", "{\"name\":\"Later draft name\"}")))
                .andExpect(status().isOk());
        http.perform(post("/api/dictionaries/versioned-segment/publish").with(writer())
                        .contentType(MediaType.APPLICATION_JSON).content(DraftRevisionFixture.lifecycle(http,
                                "/api/dictionaries/versioned-segment/publish", "{\"expectedVersion\":1}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
        http.perform(post("/api/dictionaries/versioned-segment/publish").with(writer())
                        .contentType(MediaType.APPLICATION_JSON).content(DraftRevisionFixture.lifecycle(http,
                                "/api/dictionaries/versioned-segment/publish", body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.effectiveRevision").value(1))
                .andExpect(jsonPath("$.data.name").value("Versioned Segment"));
        assertThat(jdbc.queryForObject("select count(*) from summer_dictionary_version", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from summer_dictionary_outbox", Integer.class)).isEqualTo(3);
    }

    @Test
    void draftMetadataCannotHideOrRenameEffectiveSnapshot() throws Exception {
        createDictionary();
        addItem("standard", "Standard", true);
        http.perform(post("/api/dictionaries/versioned-segment/publish").with(writer())
                .contentType(MediaType.APPLICATION_JSON).content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(http, "/api/dictionaries/versioned-segment/publish", "{\"expectedVersion\":0}")))
                .andExpect(status().isOk());
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/dictionaries/versioned-segment")
                .with(writer()).contentType(MediaType.APPLICATION_JSON)
                .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(http, "/api/dictionaries/versioned-segment", "{\"name\":\"Draft name\",\"publicReadable\":false,\"enabled\":false}")))
                .andExpect(status().isOk());
        http.perform(get("/public/enterprises/ent-acme/dictionaries/versioned-segment"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].label").value("Standard"));
        http.perform(get("/api/dictionaries/versioned-segment/effective").with(jwt().jwt(j -> j.subject("operator").claim("scope", "platform.dictionary.read")
                .claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.read"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Versioned Segment"));
    }

    @Test
    void ordinaryCreateCannotPublishByBoolean() throws Exception {
        http.perform(post("/api/dictionaries").with(writer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"boolean-bypass\",\"name\":\"Bypass\",\"published\":true,\"publicReadable\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void lifecycleRevisionsRejectCoercedValuesBeforeAnyPublication() throws Exception {
        createDictionary();
        for (String field : java.util.List.of("expectedVersion", "expectedDraftRevision", "expectedEffectiveRevision")) {
            for (String value : java.util.List.of("null", "-1", "true", "\"0\"", "0.9")) {
                String body = "{\"expectedVersion\":0,\"expectedDraftRevision\":0,\"expectedEffectiveRevision\":0}";
                body = body.replace("\"" + field + "\":0", "\"" + field + "\":" + value);
                http.perform(post("/api/dictionaries/versioned-segment/publish").with(writer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.requestId").exists());
            }
        }
        http.perform(get("/public/enterprises/ent-acme/dictionaries/versioned-segment")).andExpect(status().isNotFound());
    }

    private void createDictionary() throws Exception {
        http.perform(post("/api/dictionaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"versioned-segment\",\"name\":\"Versioned Segment\",\"published\":false,\"publicReadable\":true,\"enabled\":true}")
                        .with(writer()))
                .andExpect(status().isOk());
    }

    private void addItem(String value, String label, boolean enabled) throws Exception {
        http.perform(post("/api/dictionaries/versioned-segment/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.revised(http, "/api/dictionaries/versioned-segment/items", "{\"value\":\"" + value + "\",\"label\":\"" + label + "\",\"sortOrder\":1,\"enabled\":" + enabled + "}"))
                        .with(writer()))
                .andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor writer() {
        return jwt().jwt(token -> token.claim("scope", "platform.dictionary.write").claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).subject("operator"))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.write"));
    }
}
