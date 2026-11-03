package com.abluepoint.summer.sample.bff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "summer.sample.bff.allowed-origins=https://console.example.test")
@AutoConfigureMockMvc
@ActiveProfiles("bff-runtime")
class BffFailureHttpTest {
    @Autowired MockMvc mvc;
    @Autowired InMemoryProductSessionStore sessions;

    @Test
    void sessionTenantAndRequestGuardsKeepTheirOriginalPublicErrors() throws Exception {
        sessions.put("bff-guard-session", new OidcIdentity("guard-human", "Human",
                Instant.parse("2030-01-01T00:00:00Z"), Set.of("ent-acme")));
        for (String[] example : new String[][]{
                {"ent-acme", "missing", "{\"connectionId\":\"external\",\"tool\":\"search\"}", "401", "BFF_SESSION_REQUIRED", "Product session is missing or expired"},
                {"ent-other", "present", "{\"connectionId\":\"external\",\"tool\":\"search\"}", "403", "BFF_ENTERPRISE_CONTEXT_DENIED", "Enterprise context is not available in the Product Session"},
                {"ent-acme", "present", "{}", "400", "BFF_MCP_REQUEST_INVALID", "connectionId and tool are required"},
                {"ent-acme", "present", "{\"connectionId\":\"external\",\"tool\":\"search\",\"agentId\":\"browser\"}", "400", "BFF_AGENT_ID_FORBIDDEN", "agentId is derived by the server and cannot be supplied by the browser"}}) {
            var request = post("/bff/api/v1/enterprises/" + example[0] + "/mcp/invoke")
                    .contentType(MediaType.APPLICATION_JSON).content(example[2])
                    .header("Origin", "https://console.example.test").header("X-Request-Id", "bff-guard")
                    .header(BffCsrfFilter.CSRF_HEADER, "guard-csrf")
                    .cookie(new jakarta.servlet.http.Cookie(BffCookiePolicy.CSRF_COOKIE, "guard-csrf"));
            if ("present".equals(example[1])) request.cookie(new jakarta.servlet.http.Cookie(
                    BffCookiePolicy.PRODUCT_SESSION_COOKIE, "bff-guard-session"));
            mvc.perform(request).andExpect(status().is(Integer.parseInt(example[3])))
                    .andExpect(jsonPath("$.error.code").value(example[4]))
                    .andExpect(jsonPath("$.error.message").value(example[5]))
                    .andExpect(jsonPath("$.requestId").value("bff-guard"));
        }
    }

    @Test
    void unavailableAdapterReturnsACorrelatedSafeBusinessError() throws Exception {
        sessions.put("bff-error-session", new OidcIdentity("bff-error-human", "Human",
                Instant.parse("2030-01-01T00:00:00Z"), Set.of("ent-acme")));
        mvc.perform(post("/bff/api/v1/enterprises/ent-acme/mcp/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectionId\":\"external\",\"tool\":\"search\"}")
                        .header("X-Request-Id", "bff-unavailable")
                        .header("Origin", "https://console.example.test")
                        .header(BffCsrfFilter.CSRF_HEADER, "bff-csrf")
                        .cookie(new jakarta.servlet.http.Cookie(BffCookiePolicy.PRODUCT_SESSION_COOKIE, "bff-error-session"),
                                new jakarta.servlet.http.Cookie(BffCookiePolicy.CSRF_COOKIE, "bff-csrf")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("BFF_SUMMER_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value("Summer MCP adapter is not configured"))
                .andExpect(jsonPath("$.requestId").value("bff-unavailable"))
                .andExpect(header().string("X-Request-Id", "bff-unavailable"))
                .andExpect(jsonPath("$.error.cause").doesNotExist());
    }
}
