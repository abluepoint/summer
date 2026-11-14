package com.abluepoint.summer.sample.dictionary;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "summer.sample.parameter-events.scheduling-enabled=false")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DictionaryNotificationSseHttpTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate managedDb;
    @Autowired org.springframework.transaction.PlatformTransactionManager managedTransactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources managedInterfaces;
    private com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture managed;
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    private String readerRole;
    private String operatorRole;

    @org.junit.jupiter.api.BeforeEach
    void currentManagedAuthorization() {
        managed = new com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture(
                managedDb, managedTransactions, managedInterfaces);
        managed.configuration("ent-acme", "dictionaries", false, "writer");
        readerRole = current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"), "reader",
                (handler, path, method) -> handler.getBeanType().equals(DictionaryNotificationController.class)
                        && handler.getMethod().getName().equals("subscribe") && method.equals("GET")
                        && path.equals("/api/v1/enterprises/{enterpriseId}/dictionaries/{code}/events"));
        managed.data("ent-acme", "reader", "dictionaries", "read", "TENANT_ALL");
        operatorRole = current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"), "operator",
                (handler, path, method) -> handler.getBeanType().equals(DictionaryController.class)
                        && handler.getMethod().getName().equals("draft") && method.equals("GET")
                        && path.equals("/api/dictionaries/{code}/draft"));
        managed.data("ent-acme", "operator", "dictionaries", "read", "TENANT_ALL");
    }

    @org.junit.jupiter.api.AfterEach
    void releaseManagedAuthorization() {
        current.revoke(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"), "reader", readerRole);
        current.revoke(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"), "operator", operatorRole);
        managed.close();
    }

    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;

    @Test
    void publishCreatesTenantScopedNotificationAndSseUsesRawResumableContract() throws Exception {
        createDictionary();
        publish();

        assertThat(jdbc.queryForObject(
                "select count(*) from summer_dictionary_notification_event where tenant_id = 'ent-acme' and dictionary_code = 'live-segment'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select payload_json from summer_dictionary_notification_event where dictionary_code = 'live-segment'",
                String.class)).doesNotContain("items").contains("live-segment").contains("DICTIONARY_PUBLISHED");

        http.perform(get("/api/v1/enterprises/ent-acme/dictionaries/live-segment/events")
                        .header("Last-Event-ID", "0")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .with(reader()))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void dictionaryNotificationRequiresActiveTenantMemberAndReadCapability() throws Exception {
        createDictionary();
        http.perform(get("/api/v1/enterprises/ent-acme/dictionaries/live-segment/events")
                        .with(jwt().jwt(token -> token.subject("reader").claim("enterprise_memberships", List.of(
                                Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));

        http.perform(get("/api/v1/enterprises/ent-other/dictionaries/live-segment/events")
                        .with(readerFor("ent-other")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void invalidLastEventIdIsAStableBadRequest() throws Exception {
        createDictionary();
        http.perform(get("/api/v1/enterprises/ent-acme/dictionaries/live-segment/events")
                        .header("Last-Event-ID", "not-a-number")
                        .with(reader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_DICTIONARY_SSE_LAST_EVENT_ID"));
    }

    @Test
    void revokedReaderDoesNotReceiveSubsequentPublication() throws Exception {
        createDictionary();publish();
        var stream=http.perform(get("/api/v1/enterprises/ent-acme/dictionaries/live-segment/events").with(reader()))
                .andExpect(request().asyncStarted()).andReturn();
        assertThat(stream.getResponse().getContentAsString()).contains("event:DICTIONARY_PUBLISHED");
        current.revoke(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"), "reader", readerRole);
        http.perform(post("/api/dictionaries/live-segment/publish").with(writer()).contentType(MediaType.APPLICATION_JSON)
                .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(http, "/api/dictionaries/live-segment/publish", "{\"expectedVersion\":1}"))) .andExpect(status().isOk());
        assertThat(stream.getResponse().getContentAsString().split("event:DICTIONARY_PUBLISHED",-1)).hasSize(2);
    }

    private void createDictionary() throws Exception {
        http.perform(post("/api/dictionaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"live-segment\",\"name\":\"Live Segment\",\"publicReadable\":true}")
                        .with(writer()))
                .andExpect(status().isOk());
    }

    private void publish() throws Exception {
        http.perform(post("/api/dictionaries/live-segment/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(com.abluepoint.summer.sample.dictionary.DraftRevisionFixture.lifecycle(http, "/api/dictionaries/live-segment/publish", "{\"expectedVersion\":0}"))
                        .with(writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor writer() {
        return jwt().jwt(token -> token.claim("scope", "platform.dictionary.write").subject("writer")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of("platform.dictionary.write", "platform.dictionary.read"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.write"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor reader() {
        return readerFor("ent-acme");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor readerFor(String enterpriseId) {
        return jwt().jwt(token -> token.claim("scope", "platform.dictionary.read").subject("reader")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", enterpriseId, "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of(enterpriseId, List.of("platform.dictionary.read"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.read"));
    }
}
