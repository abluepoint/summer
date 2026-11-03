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
class DictionaryNotificationFailureHttpTest {
    @Autowired MockMvc mvc;

    @Test
    void missingDictionaryIsJsonWithCorrelationEvenWhenClientAcceptsOnlyEvents() throws Exception {
        for (MediaType accept : List.of(MediaType.ALL, MediaType.TEXT_EVENT_STREAM)) {
            mvc.perform(get("/api/v1/enterprises/ent-acme/dictionaries/absent-contract/events").accept(accept)
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
                mvc.perform(get("/api/v1/enterprises/ent-acme/dictionaries/absent-contract/events").accept(accept)
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
        return jwt().jwt(token -> token.subject("dictionary-contract")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of("platform.dictionary.read"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.dictionary.read"));
    }
}
