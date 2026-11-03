package com.abluepoint.summer.sample.bff;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BffMcpControllerTest {

    private static final String ORIGIN = "https://console.example.test";

    private final InMemoryBffStateStore stateStore = new InMemoryBffStateStore();
    private final InMemoryProductSessionStore sessions = new InMemoryProductSessionStore();
    private final RecordingSummerMcpClient summer = new RecordingSummerMcpClient();
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new BffSessionController(
                    new BffSessionService(stateStore, sessions, (code, verifier) ->
                            new OidcIdentity("unused", "Unused", Instant.now()), new BffTokenGenerator()),
                    new BffCookiePolicy("https://console.example.test/", "/"), summer))
            .setControllerAdvice(businessErrors())
            .addFilters(new BffCsrfFilter(List.of(ORIGIN)))
            .build();

    @Test
    void invokesSummerThroughPluggableClientUsingProductSessionIdentity() throws Exception {
        sessions.put("session-human", new OidcIdentity("human-1", "Alice",
                Instant.parse("2030-01-01T00:00:00Z"), java.util.Set.of("ent-acme")));

        mockMvc.perform(post("/bff/api/v1/enterprises/ent-acme/mcp/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectionId\":\"ext-1\",\"tool\":\"search\",\"arguments\":{\"query\":\"alice\"}}")
                        .header("Origin", ORIGIN)
                        .header(BffCsrfFilter.CSRF_HEADER, "csrf-1")
                        .cookie(new jakarta.servlet.http.Cookie(BffCookiePolicy.PRODUCT_SESSION_COOKIE, "session-human"),
                                new jakarta.servlet.http.Cookie(BffCookiePolicy.CSRF_COOKIE, "csrf-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identity.initiator").value("human-1"))
                .andExpect(jsonPath("$.data.identity.actor").value("human-1"))
                .andExpect(jsonPath("$.data.identity.effectiveSubject").value("human-1"))
                .andExpect(jsonPath("$.data.identity.transport").value("connection-service"))
                .andExpect(jsonPath("$.data.resultStatus").value("DEFERRED_TO_SUMMER"));

        assertThat(summer.enterpriseId).isEqualTo("ent-acme");
        assertThat(summer.identity.subject()).isEqualTo("human-1");
        assertThat(summer.request.agentId()).isNull();
    }

    @Test
    void rejectsMissingSessionAndCrossEnterpriseContext() throws Exception {
        mockMvc.perform(post("/bff/api/v1/enterprises/ent-acme/mcp/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectionId\":\"ext-1\",\"tool\":\"search\",\"arguments\":{}}")
                        .header("Origin", ORIGIN)
                        .header(BffCsrfFilter.CSRF_HEADER, "csrf-1")
                        .cookie(new jakarta.servlet.http.Cookie(BffCookiePolicy.CSRF_COOKIE, "csrf-1")))
                .andExpect(status().isUnauthorized());

        sessions.put("session-acme", new OidcIdentity("human-1", "Alice",
                Instant.parse("2030-01-01T00:00:00Z"), java.util.Set.of("ent-acme")));
        mockMvc.perform(post("/bff/api/v1/enterprises/ent-other/mcp/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectionId\":\"ext-1\",\"tool\":\"search\",\"arguments\":{}}")
                        .header("Origin", ORIGIN)
                        .header(BffCsrfFilter.CSRF_HEADER, "csrf-1")
                        .cookie(new jakarta.servlet.http.Cookie(BffCookiePolicy.PRODUCT_SESSION_COOKIE, "session-acme"),
                                new jakarta.servlet.http.Cookie(BffCookiePolicy.CSRF_COOKIE, "csrf-1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsClientSuppliedAgentOverride() throws Exception {
        sessions.put("session-human", new OidcIdentity("human-1", "Alice",
                Instant.parse("2030-01-01T00:00:00Z"), java.util.Set.of("ent-acme")));
        mockMvc.perform(post("/bff/api/v1/enterprises/ent-acme/mcp/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectionId\":\"ext-1\",\"tool\":\"search\",\"agentId\":\"forged-agent\",\"arguments\":{}}")
                        .header("Origin", ORIGIN)
                        .header(BffCsrfFilter.CSRF_HEADER, "csrf-1")
                        .cookie(new jakarta.servlet.http.Cookie(BffCookiePolicy.PRODUCT_SESSION_COOKIE, "session-human"),
                                new jakarta.servlet.http.Cookie(BffCookiePolicy.CSRF_COOKIE, "csrf-1")))
                .andExpect(status().isBadRequest());
        assertThat(summer.request).isNull();
    }

    @Test
    void rejectsMcpMutationWithoutTrustedOriginAndCsrfPair() throws Exception {
        sessions.put("session-human", new OidcIdentity("human-1", "Alice",
                Instant.parse("2030-01-01T00:00:00Z"), java.util.Set.of("ent-acme")));
        mockMvc.perform(post("/bff/api/v1/enterprises/ent-acme/mcp/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectionId\":\"ext-1\",\"tool\":\"search\",\"arguments\":{}}")
                        .header("Origin", "https://evil.example.test")
                        .header(BffCsrfFilter.CSRF_HEADER, "csrf-1")
                        .cookie(new jakarta.servlet.http.Cookie(BffCookiePolicy.PRODUCT_SESSION_COOKIE, "session-human"),
                                new jakarta.servlet.http.Cookie(BffCookiePolicy.CSRF_COOKIE, "csrf-1")))
                .andExpect(status().isForbidden());
        assertThat(summer.request).isNull();
    }

    @Test
    void failsClosedWhenSummerAdapterIsNotConfigured() throws Exception {
        InMemoryProductSessionStore localSessions = new InMemoryProductSessionStore();
        localSessions.put("session-human", new OidcIdentity("human-1", "Alice",
                Instant.parse("2030-01-01T00:00:00Z"), java.util.Set.of("ent-acme")));
        MockMvc unavailableMvc = MockMvcBuilders.standaloneSetup(new BffSessionController(
                        new BffSessionService(stateStore, localSessions, (code, verifier) ->
                                new OidcIdentity("unused", "Unused", Instant.now()), new BffTokenGenerator()),
                        new BffCookiePolicy("https://console.example.test/", "/"), new UnavailableSummerMcpClient()))
                .setControllerAdvice(businessErrors())
                .addFilters(new BffCsrfFilter(List.of(ORIGIN)))
                .build();

        unavailableMvc.perform(post("/bff/api/v1/enterprises/ent-acme/mcp/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectionId\":\"ext-1\",\"tool\":\"search\",\"arguments\":{}}")
                        .header("Origin", ORIGIN)
                        .header(BffCsrfFilter.CSRF_HEADER, "csrf-1")
                        .cookie(new jakarta.servlet.http.Cookie(BffCookiePolicy.PRODUCT_SESSION_COOKIE, "session-human"),
                                new jakarta.servlet.http.Cookie(BffCookiePolicy.CSRF_COOKIE, "csrf-1")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("BFF_SUMMER_UNAVAILABLE"));
    }

    @Test
    void returnsStableErrorEnvelopeWhenSummerAdapterFailsUnexpectedly() throws Exception {
        InMemoryProductSessionStore localSessions = new InMemoryProductSessionStore();
        localSessions.put("session-human", new OidcIdentity("human-1", "Alice",
                Instant.parse("2030-01-01T00:00:00Z"), java.util.Set.of("ent-acme")));
        for (RuntimeException upstream : List.of(new IllegalStateException("provider diagnostic"),
                new org.springframework.security.access.AccessDeniedException("provider diagnostic"),
                com.abluepoint.summer.common.error.SummerException.withMessage(
                        com.abluepoint.summer.common.error.CommonErrorCode.CONFLICT, "provider diagnostic"))) {
            MockMvc failingMvc = MockMvcBuilders.standaloneSetup(new BffSessionController(
                            new BffSessionService(stateStore, localSessions, (code, verifier) ->
                                    new OidcIdentity("unused", "Unused", Instant.now()), new BffTokenGenerator()),
                            new BffCookiePolicy("https://console.example.test/", "/"),
                            (enterpriseId, identity, request) -> { throw upstream; }))
                    .setControllerAdvice(businessErrors())
                    .addFilters(new BffCsrfFilter(List.of(ORIGIN)))
                    .build();
            var result = failingMvc.perform(post("/bff/api/v1/enterprises/ent-acme/mcp/invoke")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"connectionId\":\"ext-1\",\"tool\":\"search\",\"arguments\":{}}")
                            .header("Origin", ORIGIN)
                            .header(BffCsrfFilter.CSRF_HEADER, "csrf-1")
                            .cookie(new jakarta.servlet.http.Cookie(BffCookiePolicy.PRODUCT_SESSION_COOKIE, "session-human"),
                                    new jakarta.servlet.http.Cookie(BffCookiePolicy.CSRF_COOKIE, "csrf-1")))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error.code").value("BFF_MCP_UPSTREAM_ERROR"))
                    .andExpect(jsonPath("$.error.message").value("MCP execution is temporarily unavailable"))
                    .andExpect(jsonPath("$.error.cause").doesNotExist()).andReturn();
            assertThat(result.getResolvedException()).isInstanceOfSatisfying(
                    com.abluepoint.summer.common.error.SummerException.class,
                    error -> assertThat(error.getCause()).isSameAs(upstream));
            assertThat(result.getResponse().getContentAsString()).doesNotContain("provider diagnostic");
        }
    }

    private static com.abluepoint.summer.mvc.support.ApiExceptionHandler businessErrors() {
        return new com.abluepoint.summer.mvc.support.ApiExceptionHandler(
                (code, arguments, locale) -> code.defaultMessage(),
                new com.abluepoint.summer.mvc.error.DefaultErrorStatusResolver(Map.of(
                        com.abluepoint.summer.sample.error.SampleTransportErrorCode.BFF_UNAVAILABLE,
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        com.abluepoint.summer.sample.error.SampleTransportErrorCode.BFF_UPSTREAM_ERROR,
                        org.springframework.http.HttpStatus.BAD_GATEWAY)),
                () -> "bff-standalone");
    }

    private static final class RecordingSummerMcpClient implements SummerMcpClient {
        private String enterpriseId;
        private OidcIdentity identity;
        private BffMcpInvokeRequest request;

        @Override
        public BffMcpInvocationResult invoke(String enterpriseId, OidcIdentity identity, BffMcpInvokeRequest request) {
            this.enterpriseId = enterpriseId;
            this.identity = identity;
            this.request = request;
            return BffMcpInvocationResult.deferred(identity);
        }
    }
}
