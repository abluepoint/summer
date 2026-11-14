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
@org.springframework.context.annotation.Import(CurrentManagedAuthorizationTestFixture.class)
class NativeDenialCompatibilityHttpTest {
    @Autowired MockMvc mvc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.abluepoint.summer.security.web.JsonAccessDeniedHandler forbidden;
    @Autowired CurrentManagedAuthorizationTestFixture fixture;
    @Autowired org.springframework.jdbc.core.JdbcTemplate db;
    @org.junit.jupiter.api.BeforeEach void exactNativeServiceSeams() {
        var enterprise=com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme");
        fixture.reachServiceWithoutDelegation(enterprise,"native-boundary-member",(h,p,m)->p.startsWith("/api/v1/enterprises/{enterpriseId}/policies") || p.startsWith("/api/v1/enterprises/{enterpriseId}/menu-manifest") || p.startsWith("/api/authorization-events") || p.startsWith("/api/v1/enterprises/{e}/mcp-execution-permits"));
        fixture.reachServiceWithoutDelegation(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(),"native-boundary-member",(h,p,m)->p.startsWith("/api/v1/platform/menu-manifest"));
        fixture.grantHandlers(enterprise,"native-boundary-member",(h,p,m)->h.getBeanType().getName().endsWith(".AuthorizationController") && p.startsWith("/api/v1/enterprises/{enterpriseId}/"));
        fixture.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(),"native-boundary-member",(h,p,m)->h.getBeanType().getName().endsWith(".AuthorizationController") && p.startsWith("/api/v1/platform/"));
    }


    @Test void retiredAuthorizationAdministrationCannotWriteThroughOldAdapterEntries() throws Exception {
        String base="/api/v1/enterprises/ent-acme/";
        for(String[] entry:new String[][]{{"GET","capabilities/catalog"},{"POST","capabilities","{}"},
                {"POST","capabilities/missing:enable"},{"POST","capabilities/missing:disable"},{"PATCH","capabilities/missing","{}"},
                {"GET","capability-bundles"},{"GET","capability-bundles/missing"},{"GET","capability-bundles/missing/versions"},
                {"POST","capability-bundles","{}"},{"PATCH","capability-bundles/missing","{}"},{"PUT","capability-bundles/missing/capabilities","{}"}})
            retired(endpoint(entry[0],base+entry[1],entry.length==3?entry[2]:null));
        // Roles now use the current permission controller. Native grants/claims cannot bypass it;
        // positive role/assignment/version semantics are retained in AuthorizationControllerTest/AuthorizationAssignmentHttpTest.
        mvc.perform(get(base+"roles").with(member())).andExpect(status().isForbidden());
    }
    @Test void platformCapabilityApisAreRetiredWithoutWrites() throws Exception {
        retired(get("/api/v1/platform/capabilities/catalog"));
        retired(endpoint("POST","/api/v1/platform/capabilities","{}"));
    }
    @Test void retiredEnterpriseCapabilityCreationCannotCreatePlatformScope() throws Exception {
        retired(endpoint("POST","/api/v1/enterprises/ent-acme/capabilities",
                "{\"code\":\"native.platform\",\"resource\":\"native-resource\",\"action\":\"read\",\"scope\":\"PLATFORM\"}"));
    }
    private void retired(MockHttpServletRequestBuilder request) throws Exception {
        var before=retiredCounts();
        mvc.perform(request.with(member()).header("If-Match","\"1\"").header("Idempotency-Key","retired-native").header("X-Request-Id","retired-native"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("SUMMER_AUTHORIZATION_INVALID"))
                .andExpect(jsonPath("$.error.message").value("Legacy code, deny and bundle grant APIs are retired; use permission and role management"))
                .andExpect(jsonPath("$.requestId").value("retired-native"));
        org.assertj.core.api.Assertions.assertThat(retiredCounts()).isEqualTo(before);
        org.assertj.core.api.Assertions.assertThat(db.queryForObject("select engine_mode from summer_authorization_domain where domain_key='ENTERPRISE:ent-acme'",String.class)).isEqualTo("MANAGED");
    }

    private Map<String,Long> retiredCounts() {
        var counts=new java.util.LinkedHashMap<String,Long>();
        for(String table:List.of("summer_authorization_capability","summer_authorization_user_binding","summer_authorization_role","summer_permission_mutation"))
            counts.put(table,db.queryForObject("select count(*) from "+table,Long.class));
        return counts;
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
        org.mockito.Mockito.clearInvocations(forbidden);
        mvc.perform(get("/api/v1/enterprises/ent-acme/menu-manifest").with(user("native-session-principal"))
                        .header("X-Request-Id", "native-session-denial"))
                .andExpect(status().isForbidden()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Access is denied"))
                .andExpect(jsonPath("$.requestId").value("native-session-denial"))
                .andExpect(header().string("X-Request-Id", "native-session-denial"))
                .andExpect(jsonPath("$.error.cause").doesNotExist())
                .andExpect(result -> assertNull(result.getResolvedException()));
        var denied = org.mockito.ArgumentCaptor.forClass(AccessDeniedException.class);
        org.mockito.Mockito.verify(forbidden).handle(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), denied.capture());
        assertInstanceOf(org.springframework.security.authorization.AuthorizationDeniedException.class, denied.getValue());
    }

    @Test void authorizationEventListAndRetryKeepTheirOriginalServiceDenial() throws Exception {
        denied(get("/api/authorization-events").queryParam("enterpriseId", "ent-acme"),
                member(), "Current management resource permission required");
        denied(post("/api/authorization-events/missing/retry").queryParam("enterpriseId", "ent-acme"),
                member(), "Current management resource permission required");
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
        mvc.perform(get("/api/v1/enterprises/ent-acme/capabilities/catalog").with(jwt()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.message").value("Access is denied"));
        mvc.perform(get("/api/v1/enterprises/ent-acme/roles").with(jwt().jwt(t->t.subject("native-managed-no-role").claim("summer_managed_grants",true))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.message").value("Access is denied"));
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
                    if (List.of("Policy capability denied", "Platform menu capability denied",
                            "Authorization capability denied").contains(message)) {
                        var cause = assertInstanceOf(AccessDeniedException.class, origin(result).getCause());
                        assertEquals("Current management resource permission required", cause.getMessage());
                    }
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
        return jwt().jwt(t -> t.subject("native-boundary-member").claim("scope",String.join(" ",capabilities))
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
