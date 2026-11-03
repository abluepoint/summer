package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NativeDenialCompatibilityHttpTest {
    @Autowired MockMvc mvc;

    @Test void authorizationAdministrationKeepsItsOwnDenialAcrossTheOldAdapterEntries() throws Exception {
        String base = "/api/v1/enterprises/ent-acme/";
        for (String[] entry : new String[][]{
                {"GET", "capabilities/catalog"}, {"POST", "capabilities", "{}"},
                {"POST", "capabilities/missing:enable"}, {"POST", "capabilities/missing:disable"},
                {"PATCH", "capabilities/missing", "{}"},
                {"GET", "roles"}, {"GET", "roles/missing"}, {"POST", "roles", "{}"},
                {"PATCH", "roles/missing", "{}"}, {"PUT", "roles/missing/capabilities", "{}"},
                {"GET", "roles/missing/capabilities"}, {"GET", "capability-bundles"},
                {"GET", "capability-bundles/missing"}, {"GET", "capability-bundles/missing/versions"},
                {"POST", "capability-bundles", "{}"}, {"PATCH", "capability-bundles/missing", "{}"},
                {"PUT", "capability-bundles/missing/capabilities", "{}"},
                {"GET", "authorization-audit/missing"}, {"PUT", "users/native-user/roles", "{}"},
                {"GET", "users/native-user/roles"}}) {
            denied(endpoint(entry[0], base + entry[1], entry.length == 3 ? entry[2] : null),
                    member(), "Authorization capability denied");
        }
    }

    @Test void platformCapabilityChecksKeepTheirOriginalMessages() throws Exception {
        denied(get("/api/v1/platform/capabilities/catalog"), member(), "Platform capability denied");
        denied(endpoint("POST", "/api/v1/platform/capabilities", "{}"), member(), "Platform capability denied");
    }

    @Test void authorizedEnterpriseCapabilityCreationCannotCreatePlatformScopeAndKeepsItsOriginalMessage() throws Exception {
        denied(endpoint("POST", "/api/v1/enterprises/ent-acme/capabilities",
                        "{\"code\":\"native.platform\",\"resource\":\"native-resource\",\"action\":\"read\",\"scope\":\"PLATFORM\"}"),
                member("enterprise.capabilities.write"), "Platform capability requires platform principal");
        mvc.perform(get("/api/v1/enterprises/ent-acme/capabilities/catalog")
                        .with(member("enterprise.capabilities.read")))
                .andExpect(status().isOk()).andExpect(result -> {
                    List<String> codes = JsonPath.read(result.getResponse().getContentAsString(), "$.data.capabilities[*].code");
                    assertFalse(codes.contains("native.platform"));
                });
    }

    @Test void policyAdministrationKeepsItsOriginalDenialAcrossLifecycleAndReadEntries() throws Exception {
        String base = "/api/v1/enterprises/ent-acme/policies";
        for (String[] entry : new String[][]{
                {"GET", ""}, {"POST", "", "{}"}, {"GET", "/missing"}, {"GET", "/missing/versions"},
                {"PATCH", "/missing/draft", "{}"}, {"POST", "/missing:validate"}, {"POST", "/missing:publish"},
                {"POST", "/missing:pause"}, {"POST", "/missing:revoke"},
                {"POST", "/missing:rollback", "{}"}, {"POST", "/missing:copy", "{}"},
                {"GET", "/missing/test-cases"}, {"POST", "/missing/test-cases", "{}"},
                {"POST", "/missing:test"}, {"GET", "/missing/audit"}}) {
            denied(endpoint(entry[0], base + entry[1], entry.length == 3 ? entry[2] : null), member(), "Policy capability denied");
        }
    }

    @Test void policyEvaluationKeepsControllerIdentityAndInternalCallerDenials() throws Exception {
        String path = "/internal/v1/authorization/evaluate";
        denied(endpoint("POST", path, "{\"actor\":\"forged\"}"), member(),
                "Evaluation identity does not match trusted caller");
        denied(endpoint("POST", path, "{}"), member(), "Internal authorization caller denied");
        denied(endpoint("POST", path, "{}").with(csrf()), user("native-session-principal"), "Trusted execution identity required");
    }

    @Test void menuManagementKeepsPlatformEnterpriseAndMembershipGuardMessages() throws Exception {
        denied(get("/api/v1/platform/menu-manifest"), member(), "Platform menu capability denied");
        denied(endpoint("PUT", "/api/v1/platform/menu-manifest", "{\"manifestVersion\":0}"),
                member(), "Platform menu capability denied");
        denied(get("/api/v1/enterprises/ent-acme/menu-manifest/management"), member(), "Authorization capability denied");
        denied(endpoint("PATCH", "/api/v1/enterprises/ent-acme/menu-manifest/missing", "{}"),
                member(), "Authorization capability denied");
        denied(endpoint("PATCH", "/api/v1/enterprises/ent-acme/menu-manifest", "{}").queryParam("menuId", "missing"),
                member(), "Authorization capability denied");
        denied(get("/api/v1/enterprises/ent-acme/menu-manifest"), user("native-session-principal"),
                "Enterprise membership required");
    }

    @Test void authorizationEventListAndRetryKeepTheirOriginalServiceDenial() throws Exception {
        denied(get("/api/authorization-events").queryParam("enterpriseId", "ent-acme"),
                member(), "Authorization capability denied");
        denied(post("/api/authorization-events/missing/retry").queryParam("enterpriseId", "ent-acme"),
                member(), "Authorization capability denied");
    }

    @Test void mcpDecisionKeepsTrustTenantIdentityAndAudienceDenials() throws Exception {
        String path = "/internal/v1/mcp/execution-decisions";
        denied(endpoint("POST", path, "{}"), member(), "Connection service trust denied");
        String body = decision();
        denied(endpoint("POST", path, body.replace("ent-acme", "ent-other")), service("cs-native"), "Enterprise context denied");
        denied(endpoint("POST", path, body.replace("\"actorPrincipalId\":\"agent\"", "\"actorPrincipalId\":\"forged\"")),
                service("cs-native"), "Caller identity mismatch");
        denied(endpoint("POST", path, body.replace("urn:aict:cs-native", "urn:wrong")), service("cs-native"), "Permit audience mismatch");
    }

    @Test void mcpClaimAndRevokeKeepTheirOriginalDenialAndDoNotConsumeThePermit() throws Exception {
        MvcResult created = mvc.perform(endpoint("POST", "/internal/v1/mcp/execution-decisions", decision())
                        .with(service("cs-native"))).andExpect(status().isOk()).andReturn();
        String permit = JsonPath.read(created.getResponse().getContentAsString(), "$.data.permit.permitId");
        denied(endpoint("POST", "/internal/v1/mcp/execution-permits/" + permit + ":claim", "{\"executionAttemptId\":\"native-attempt\"}"),
                service("cs-other"), "Permit audience service mismatch");
        mvc.perform(endpoint("POST", "/internal/v1/mcp/execution-permits/" + permit + ":claim", "{\"executionAttemptId\":\"native-attempt\"}")
                        .with(service("cs-native"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CLAIMED"));
        denied(post("/api/v1/enterprises/ent-acme/mcp-execution-permits/" + permit + ":revoke"),
                member(), "MCP execution capability denied");
    }

    @Test void preControllerTenantManagedGrantAndAmbiguousQueryDenialsRetainTheGlobalMessage() throws Exception {
        priorDenial(get("/api/v1/enterprises/ent-acme/capabilities/catalog"), jwt(), "Inactive enterprise membership");
        priorDenial(get("/api/v1/enterprises/ent-acme/capabilities/catalog"),
                jwt().jwt(t -> t.subject("native-managed-no-role").claim("summer_managed_grants", true)
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))),
                "Authorization denied: DEFAULT_DENY");
        priorDenial(get("/api/authorization-events").queryParam("enterpriseId", "ent-acme", "ent-other"),
                member(), "A single valid enterpriseId is required");
    }

    private void denied(MockHttpServletRequestBuilder request, RequestPostProcessor identity, String message) throws Exception {
        mvc.perform(request.with(identity).header("If-Match", "\"1\"").header("Idempotency-Key", "native-denied")
                        .header("X-Request-Id", "native-denial"))
                // This assertion precedes the API assertion: a pre-handler rejection cannot be mistaken for the target guard.
                .andExpect(result -> assertEquals(message, origin(result).getMessage()))
                .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value(message))
                .andExpect(jsonPath("$.requestId").value("native-denial"))
                .andExpect(header().string("X-Request-Id", "native-denial"))
                .andExpect(jsonPath("$.error.cause").doesNotExist())
                .andExpect(result -> {
                    SummerException error = assertInstanceOf(SummerException.class, result.getResolvedException());
                    assertSame(CommonErrorCode.FORBIDDEN, error.errorCode());
                    assertSame(origin(result), error.getCause());
                });
    }

    private void priorDenial(MockHttpServletRequestBuilder request, RequestPostProcessor identity, String message) throws Exception {
        mvc.perform(request.with(identity).header("X-Request-Id", "prior-denial"))
                .andExpect(result -> assertEquals(message, assertInstanceOf(AccessDeniedException.class, result.getResolvedException()).getMessage()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Access is denied"))
                .andExpect(jsonPath("$.requestId").value("prior-denial"));
    }

    private static AccessDeniedException origin(MvcResult result) {
        Throwable failure = result.getResolvedException();
        if (failure instanceof SummerException summer) failure = summer.getCause();
        return assertInstanceOf(AccessDeniedException.class, failure);
    }

    private static MockHttpServletRequestBuilder endpoint(String method, String path, String body) {
        var builder = request(HttpMethod.valueOf(method), path);
        return body == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestPostProcessor member(String... capabilities) {
        return jwt().jwt(t -> t.subject("native-boundary-member")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of(capabilities))))
                .authorities(Arrays.stream(capabilities).map(c -> new SimpleGrantedAuthority("SCOPE_" + c)).toArray(SimpleGrantedAuthority[]::new));
    }

    private static RequestPostProcessor service(String id) {
        return jwt().jwt(t -> t.subject("native-connection-service").claim("connection_service_id", id)
                        .claim("service_trust_verified", true).claim("execution_context_id", "native-context")
                        .claim("actor_principal_id", "agent").claim("effective_subject_principal_id", "user"))
                .authorities(new SimpleGrantedAuthority("SCOPE_internal.mcp.execution.decide"));
    }

    private static String decision() {
        return "{\"enterpriseId\":\"ent-acme\",\"connectionServiceId\":\"cs-native\",\"connectionBindingId\":\"binding\","
                + "\"externalConnectionId\":\"external\",\"executionContextId\":\"native-context\",\"actorPrincipalId\":\"agent\","
                + "\"effectiveSubjectPrincipalId\":\"user\",\"toolId\":\"tool\",\"toolName\":\"search\",\"schemaDigest\":\"schema\","
                + "\"argumentsDigest\":\"args\",\"audience\":\"urn:aict:cs-native\",\"risk\":\"LOW\"}";
    }
}
