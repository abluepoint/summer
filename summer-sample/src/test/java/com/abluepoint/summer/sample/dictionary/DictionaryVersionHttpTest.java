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
                        .content("{\"expectedVersion\":0}")
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
                        .content("{\"expectedVersion\":1}")
                        .with(writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        http.perform(post("/api/dictionaries/versioned-segment/disable")
                        .header("X-Request-Id", "disable-v2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}")
                        .with(writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
        http.perform(get("/public/enterprises/ent-acme/dictionaries/versioned-segment"))
                .andExpect(status().isNotFound());

        http.perform(post("/api/dictionaries/versioned-segment/rollback")
                        .header("X-Request-Id", "rollback-v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2,\"targetVersion\":1}")
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
                        .content("{\"expectedVersion\":0}")
                        .with(writer()))
                .andExpect(status().isOk());

        http.perform(post("/api/dictionaries/versioned-segment/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}")
                        .with(writer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_DICTIONARY_VERSION_CONFLICT"));

        http.perform(post("/api/dictionaries/versioned-segment/rollback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"targetVersion\":9}")
                        .with(writer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_DICTIONARY_INVALID_STATE"));

        http.perform(post("/api/dictionaries/versioned-segment/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_customer.read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void idempotencyKeyReplaysTheCommittedPublishWithoutCreatingAnotherVersion() throws Exception {
        createDictionary();
        addItem("standard", "Standard", true);
        String body = "{\"expectedVersion\":0,\"idempotencyKey\":\"publish-once\"}";
        http.perform(post("/api/dictionaries/versioned-segment/publish")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(writer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        http.perform(post("/api/dictionaries/versioned-segment/publish")
                        .contentType(MediaType.APPLICATION_JSON).content(body).with(writer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        assertThat(jdbc.queryForObject("select count(*) from summer_dictionary_version", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from summer_dictionary_outbox", Integer.class)).isEqualTo(1);
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
                        .content("{\"value\":\"" + value + "\",\"label\":\"" + label + "\",\"sortOrder\":1,\"enabled\":" + enabled + "}")
                        .with(writer()))
                .andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor writer() {
        return jwt().jwt(token -> token.claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).subject("operator"))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.write"));
    }
}
