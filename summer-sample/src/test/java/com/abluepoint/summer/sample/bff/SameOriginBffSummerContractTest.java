package com.abluepoint.summer.sample.bff;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jayway.jsonpath.JsonPath;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Executable same-origin contract seam: browser Product Session -> BFF -> Summer MCP APIs.
 *
 * <p>The test uses only local Summer Sample adapters. It does not claim external OIDC,
 * ConnectionService, OAuth resource-token or MCP-provider interoperability.</p>
 */
@SpringBootTest(properties = {
        "summer.sample.bff.allowed-origins=https://console.example.test"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@ActiveProfiles("bff-runtime")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import({SameOriginBffSummerContractTest.LocalContractConfiguration.class, com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class})
class SameOriginBffSummerContractTest {

    private static final String ORIGIN = "https://console.example.test";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    InMemoryProductSessionStore sessions;

    @org.springframework.beans.factory.annotation.Autowired
    com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;

    @org.junit.jupiter.api.BeforeEach
    void grantCurrentInterfaces() throws Exception {
        current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"), "audit-admin",
                (handler, path, method) -> handler.getBeanType().getName().equals(
                        "com.abluepoint.summer.sample.auditledger.AuditLedgerController")
                        && method.equals("GET") && path.equals("/api/v1/enterprises/{enterpriseId}/mcp-invocations/{invocationId}"));
    }

    @Test
    void humanProductSessionInvokesSummerAndReturnsPhysicalExecutionEvidence() throws Exception {
        sessions.put("session-human", new OidcIdentity(
                "human-1", "Alice", Instant.parse("2030-01-01T00:00:00Z"), Set.of("ent-acme")));

        MvcResult bffResponse = mockMvc.perform(post("/bff/api/v1/enterprises/ent-acme/mcp/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectionId\":\"ext-1\",\"tool\":\"search\",\"arguments\":{\"query\":\"alice\"}}")
                        .header("Origin", ORIGIN)
                        .header(BffCsrfFilter.CSRF_HEADER, "csrf-e2e")
                        .cookie(new jakarta.servlet.http.Cookie(BffCookiePolicy.PRODUCT_SESSION_COOKIE, "session-human"),
                                new jakarta.servlet.http.Cookie(BffCookiePolicy.CSRF_COOKIE, "csrf-e2e")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identity.initiator").value("human-1"))
                .andExpect(jsonPath("$.data.identity.actor").value("human-1"))
                .andExpect(jsonPath("$.data.identity.effectiveSubject").value("human-1"))
                .andExpect(jsonPath("$.data.identity.transport").value("connection-service"))
                .andExpect(jsonPath("$.data.resultStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.invocationId").isNotEmpty())
                .andReturn();

        String invocationId = JsonPath.read(bffResponse.getResponse().getContentAsString(), "$.data.invocationId");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/v1/enterprises/ent-acme/mcp-invocations/{invocationId}", invocationId)
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invocationId").value(invocationId))
                .andExpect(jsonPath("$.data.enterpriseId").value("ent-acme"))
                .andExpect(jsonPath("$.data.actor").value("human-1"))
                .andExpect(jsonPath("$.data.effectiveSubject").value("human-1"))
                .andExpect(jsonPath("$.data.resultStatus").value("SUCCEEDED"));
    }

    @Test
    void upstreamFailureReturnsSanitizedErrorEnvelopeAtSameOriginBoundary() throws Exception {
        sessions.put("session-human", new OidcIdentity(
                "human-1", "Alice", Instant.parse("2030-01-01T00:00:00Z"), Set.of("ent-acme")));

        mockMvc.perform(post("/bff/api/v1/enterprises/ent-acme/mcp/invoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectionId\":\"ext-1\",\"tool\":\"explode\",\"arguments\":{}}")
                        .header("Origin", ORIGIN)
                        .header(BffCsrfFilter.CSRF_HEADER, "csrf-e2e")
                        .cookie(new jakarta.servlet.http.Cookie(BffCookiePolicy.PRODUCT_SESSION_COOKIE, "session-human"),
                                new jakarta.servlet.http.Cookie(BffCookiePolicy.CSRF_COOKIE, "csrf-e2e")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("BFF_MCP_UPSTREAM_ERROR"))
                .andExpect(jsonPath("$.error.message").value("MCP execution is temporarily unavailable"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token.subject("audit-admin").claim("scope", "enterprise.audit.read")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.audit.read"))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_enterprise.audit.read"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LocalContractConfiguration {
        @Bean
        @Primary
        SummerMcpClient testSummerMcpClient(ObjectProvider<MockMvc> mockMvc) {
            return new InProcessSummerMcpClient(mockMvc);
        }
    }

    /** Test-only HTTP adapter: calls the same internal HTTP contracts as a BFF deployment. */
    private static final class InProcessSummerMcpClient implements SummerMcpClient {
        private final ObjectProvider<MockMvc> mockMvc;

        private InProcessSummerMcpClient(ObjectProvider<MockMvc> mockMvc) {
            this.mockMvc = mockMvc;
        }

        @Override
        public BffMcpInvocationResult invoke(String enterpriseId, OidcIdentity identity,
                                             BffMcpInvokeRequest request) {
            try {
                if ("explode".equals(request.tool())) {
                    throw new org.springframework.security.access.AccessDeniedException("provider diagnostic");
                }
                String context = "ctx-bff-" + identity.subject();
                String attempt = "attempt-bff-" + identity.subject();
                String toolId = "tool-" + request.tool();
                String decisionBody = "{\"enterpriseId\":\"" + enterpriseId
                        + "\",\"connectionServiceId\":\"cs-1\",\"connectionBindingId\":\"bind-1\","
                        + "\"externalConnectionId\":\"" + request.connectionId()
                        + "\",\"connectionRevision\":1,\"executionContextId\":\"" + context
                        + "\",\"actorPrincipalId\":\"" + identity.subject()
                        + "\",\"actorInstanceId\":null,\"effectiveSubjectPrincipalId\":\""
                        + identity.subject() + "\",\"toolId\":\"" + toolId
                        + "\",\"toolName\":\"" + request.tool()
                        + "\",\"schemaDigest\":\"schema-bff\",\"argumentsDigest\":\"args-bff\""
                        + ",\"audience\":\"urn:aict:cs-1\",\"risk\":\"LOW\"}";
                MvcResult decision = mockMvc.getObject().perform(post("/internal/v1/mcp/execution-decisions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(decisionBody)
                                .with(serviceIdentity(context, identity.subject())))
                        .andExpect(status().isOk())
                        .andReturn();
                String permitId = JsonPath.read(decision.getResponse().getContentAsString(), "$.data.permit.permitId");
                String invocationId = JsonPath.read(decision.getResponse().getContentAsString(), "$.data.invocationId");
                String invokeBody = "{\"permitId\":\"" + permitId + "\",\"executionAttemptId\":\""
                        + attempt + "\",\"schemaDigest\":\"schema-bff\",\"argumentsDigest\":\"args-bff\""
                        + ",\"sessionRef\":\"session-bff\"}";
                MvcResult execution = mockMvc.getObject().perform(post("/internal/v1/connections/{connection}/tools/{tool}:invoke",
                                        request.connectionId(), request.tool())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invokeBody)
                                .with(serviceIdentity(context, identity.subject())))
                        .andExpect(status().isOk())
                        .andReturn();
                String resultStatus = JsonPath.read(execution.getResponse().getContentAsString(), "$.data.resultStatus");
                return new BffMcpInvocationResult(
                        new BffMcpInvocationIdentity(identity.subject(), identity.subject(), identity.subject(),
                                "connection-service"), resultStatus, invocationId);
            } catch (Exception exception) {
                throw new IllegalStateException("Local Summer HTTP contract failed", exception);
            }
        }

        private static org.springframework.test.web.servlet.request.RequestPostProcessor serviceIdentity(
                String context, String subject) {
            return jwt().jwt(token -> token.subject("connection-service")
                            .claim("connection_service_id", "cs-1")
                            .claim("service_trust_verified", true)
                            .claim("execution_context_id", context)
                            .claim("initiator_principal_id", subject)
                            .claim("actor_principal_id", subject)
                            .claim("effective_subject_principal_id", subject))
                    .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_internal.mcp.execution.decide"),
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_internal.audit.write"));
        }
    }
}
