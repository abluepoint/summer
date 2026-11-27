package com.abluepoint.summer.sample.dictionary;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="summer.sample.parameter-events.scheduling-enabled=false")
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
@org.springframework.test.annotation.DirtiesContext
class DictionaryNotificationFailureHttpTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate managedDb;
    @Autowired org.springframework.transaction.PlatformTransactionManager managedTransactions;
    @Autowired com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedInterfaceResources managedInterfaces;
    private com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture managed;
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    private String readerRole;

    @org.junit.jupiter.api.BeforeEach
    void currentManagedAuthorization() {
        managed = new com.abluepoint.summer.sample.authorization.BusinessManagedAuthorizationTestFixture(
                managedDb, managedTransactions, managedInterfaces);
        readerRole = current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), "dictionary-contract",
                (handler, path, method) -> handler.getBeanType().equals(DictionaryNotificationController.class)
                        && handler.getMethod().getName().equals("subscribe") && method.equals("GET")
                        && path.equals("/api/v1/platform/dictionaries/{code}/events"));
        managed.data("ent-acme", "dictionary-contract", "dictionaries", "read", "TENANT_ALL");
    }

    @org.junit.jupiter.api.AfterEach
    void releaseManagedAuthorization() {
        current.revoke(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), "dictionary-contract", readerRole);
        managed.close();
    }


    @Autowired MockMvc mvc;

    @Test
    void missingDictionaryIsJsonWithCorrelationEvenWhenClientAcceptsOnlyEvents() throws Exception {
        for (MediaType accept : List.of(MediaType.ALL, MediaType.TEXT_EVENT_STREAM)) {
            mvc.perform(get("/api/v1/platform/dictionaries/absent-contract/events").accept(accept)
                            .header("X-Request-Id", "dictionary-missing").with(reader()))
                    .andExpect(status().isNotFound()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error.code").value("SUMMER_NOT_FOUND"))
                    .andExpect(jsonPath("$.error.message").value("Dictionary was not found"))
                    .andExpect(jsonPath("$.error.violations").isEmpty())
                    .andExpect(jsonPath("$.requestId").value("dictionary-missing"))
                    .andExpect(header().string("X-Request-Id", "dictionary-missing"))
                    .andExpect(request().asyncNotStarted());
        }
    }

    @Test
    void invalidCursorsAreJsonBeforeStartingTheStream() throws Exception {
        for (MediaType accept : List.of(MediaType.ALL, MediaType.TEXT_EVENT_STREAM)) {
            for (String cursor : List.of("invalid", "-1", "9223372036854775808")) {
                mvc.perform(get("/api/v1/platform/dictionaries/absent-contract/events").accept(accept)
                                .header("Last-Event-ID", cursor).header("X-Request-Id", "dictionary-cursor").with(reader()))
                        .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.error.code").value("SUMMER_DICTIONARY_SSE_LAST_EVENT_ID"))
                        .andExpect(jsonPath("$.error.message").value("Last-Event-ID must be a non-negative integer"))
                        .andExpect(jsonPath("$.error.violations").isEmpty())
                        .andExpect(jsonPath("$.requestId").value("dictionary-cursor"))
                        .andExpect(header().string("X-Request-Id", "dictionary-cursor"))
                        .andExpect(request().asyncNotStarted());
            }
        }
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor reader() {
        return jwt().jwt(token -> token.subject("dictionary-contract").claim("scope", "platform.dictionary.read")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of("platform.dictionary.read"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.read"));
    }
}
