package com.abluepoint.summer.sample;

import com.abluepoint.summer.data.jpa.query.api.JpaSqlTemplate;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.data.redis.host=${SUMMER_REDIS_HOST:127.0.0.1}",
        "spring.data.redis.port=${SUMMER_REDIS_PORT:6379}",
        "spring.data.redis.username=${SUMMER_REDIS_USERNAME:}",
        "spring.data.redis.password=${SUMMER_REDIS_PASSWORD:}",
        "spring.data.redis.database=${SUMMER_TEST_REDIS_DATABASE:15}",
        "spring.data.redis.timeout=2s",
        "spring.data.redis.connect-timeout=2s"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class SummerEndToEndTest {

    private static final String CLIENT_ID = "summer-client";
    private static final String CLIENT_SECRET = "summer-client-demo-only";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JpaSqlTemplate sqlTemplate;

    @Autowired
    SampleDataProperties dataProperties;

    @Autowired
    ApplicationContext applicationContext;

    @Value("${summer.security.authorization-server.user.username}")
    String localUser;

    @Value("${summer.security.authorization-server.user.password}")
    String localPassword;

    @Test
    void localConsoleBootstrapCanReadOwnSessionAndManageRolesButCannotAccessBusinessData() throws Exception {
        assertThat(applicationContext.getBeansOfType(org.springframework.security.web.authentication.AuthenticationSuccessHandler.class))
                .containsOnlyKeys("localConsoleAuthenticationSuccessHandler");
        MvcResult login = mockMvc.perform(post("/login")
                        .param("username", localUser)
                .param("password", localPassword)
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "http://127.0.0.1:5173/dashboard/workplace"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/v1/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enterpriseMemberships[0].enterpriseId").value("ent-acme"));
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/roles").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/applications").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/platform/enterprise-reviews").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/customers").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void issuesTokenAndUsesItForAProtectedCustomerRead() throws Exception {
        String token = accessToken("customer.read");

        mockMvc.perform(get("/api/customers/{username}", "summer.demo")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-Id", "e2e-request"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "e2e-request"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.displayName").value("Summer Demo"));
    }

    @Test
    void createsAndReadsCustomerWhileCountReflectsTheNewRow() throws Exception {
        long countBefore = activeCustomerCount();
        String writeToken = accessToken("customer.write");

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + writeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"created\",\"email\":\"created@summer.local\",\"displayName\":\"Created Customer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("created@summer.local"))
                .andExpect(jsonPath("$.data.displayName").value("Created Customer"));

        assertThat(activeCustomerCount()).isEqualTo(countBefore + 1);

        String readToken = accessToken("customer.read");
        mockMvc.perform(get("/api/customers/{username}", "created")
                        .header("Authorization", "Bearer " + readToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("created@summer.local"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void rejectsUnknownFieldsWhenCreatingCustomers() throws Exception {
        String token = accessToken("customer.write");

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"strict","email":"strict@summer.local","displayName":"Strict","nickname":"extra"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.error.violations[0].field").value("nickname"));
    }

    @Test
    void rejectsMalformedJsonWhenCreatingCustomers() throws Exception {
        String token = accessToken("customer.write");

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.error.violations[0].field").value("$"));
    }

    @Test
    void listsCustomersWithBoundedPaginationAndStableDefaultOrdering() throws Exception {
        String token = accessToken("customer.read");

        MvcResult first = mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.projection").value("detail"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andReturn();
        MvcResult second = mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andReturn();

        List<?> firstItems = JsonPath.read(first.getResponse().getContentAsString(), "$.data.items");
        List<?> secondItems = JsonPath.read(second.getResponse().getContentAsString(), "$.data.items");
        assertThat(secondItems).isEqualTo(firstItems);
    }

    @Test
    void rejectsInvalidCustomerListParametersAsAggregatedFieldViolations() throws Exception {
        String token = accessToken("customer.read");

        mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .queryParam("page", "-1")
                        .queryParam("size", "101")
                        .queryParam("sort", "drop-table")
                        .queryParam("direction", "sideways")
                        .queryParam("projection", "everything"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.error.violations.length()").value(5));
    }

    @Test
    void listsCustomersWithAllowlistedFilteringSortingAndProjection() throws Exception {
        String token = accessToken("customer.read");

        mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .queryParam("active", "true")
                        .queryParam("sort", "displayName")
                        .queryParam("direction", "desc")
                        .queryParam("projection", "summary")
                        .queryParam("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projection").value("summary"))
                .andExpect(jsonPath("$.data.items.length()").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].email").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].active").doesNotExist());
    }

    @Test
    void rejectsDuplicateCustomerWithConflictResponse() throws Exception {
        String token = accessToken("customer.write");

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"summer.demo\",\"email\":\"demo@summer.local\",\"displayName\":\"Duplicate\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SUMMER_CONFLICT"))
                .andExpect(jsonPath("$.error.violations[0].field").value("username"));

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"different-demo\",\"email\":\"demo@summer.local\",\"displayName\":\"Duplicate\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.violations[0].field").value("email"));
    }

    @Test
    void emptyPatchReturnsTheUnchangedCustomerAndEmailConflictNamesTheField() throws Exception {
        String token = accessToken("customer.write");
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"patch-conflict-owner\",\"email\":\"patch-conflict@summer.local\",\"displayName\":\"Owner\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/customers/{username}", "summer.demo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("summer.demo"))
                .andExpect(jsonPath("$.data.email").value("demo@summer.local"));
        mockMvc.perform(patch("/api/customers/{username}", "summer.demo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"patch-conflict@summer.local\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.violations[0].field").value("email"));
    }

    @Test
    void partiallyUpdatesCustomersWithOmissionAndExplicitNullSemantics() throws Exception {
        String token = accessToken("customer.write");
        String email = "patch@summer.local";

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"patch\",\"email\":\"" + email + "\",\"displayName\":\"Before Patch\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/customers/{username}", email.substring(0,email.indexOf('@')))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Before Patch"))
                .andExpect(jsonPath("$.data.active").value(false));

        String readToken = accessToken("customer.read");
        mockMvc.perform(get("/api/customers/{username}", email.substring(0,email.indexOf('@')))
                        .header("Authorization", "Bearer " + readToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
        MvcResult inactiveCustomers = mockMvc.perform(get("/api/customers")
                        .header("Authorization", "Bearer " + readToken)
                        .queryParam("active", "false")
                        .queryParam("size", "100"))
                .andExpect(status().isOk())
                .andReturn();
        List<String> inactiveEmails = JsonPath.read(
                inactiveCustomers.getResponse().getContentAsString(),
                "$.data.items[*].email"
        );
        assertThat(inactiveEmails).contains(email);
    }

    @Test
    void rejectsImmutableAndUnknownPatchFieldsWithNullViolations() throws Exception {
        String token = accessToken("customer.write");

        mockMvc.perform(patch("/api/customers/{username}", "summer.demo")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"changed@summer.local","active":null,"extra":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.error.violations.length()").value(2));
    }

    @Test
    void returnsNotFoundWhenPatchingAMissingCustomer() throws Exception {
        String token = accessToken("customer.write");

        mockMvc.perform(patch("/api/customers/{username}", "missing-patch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUMMER_NOT_FOUND"));
    }

    @Test
    void physicallyDeletesCustomersAndReturnsNotFoundForRepeatedDelete() throws Exception {
        String writeToken = accessToken("customer.write");
        String email = "delete@summer.local";

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + writeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"delete\",\"email\":\"" + email + "\",\"displayName\":\"Delete Me\"}"))
                .andExpect(status().isOk());

        String readToken = accessToken("customer.read");
        mockMvc.perform(delete("/api/customers/{username}", "delete")
                        .header("Authorization", "Bearer " + readToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/customers/{username}", email.substring(0,email.indexOf('@')))
                        .header("Authorization", "Bearer " + writeToken))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEmpty());

        mockMvc.perform(get("/api/customers/{username}", email.substring(0,email.indexOf('@')))
                        .header("Authorization", "Bearer " + readToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/customers/{username}", email.substring(0,email.indexOf('@')))
                .header("Authorization", "Bearer " + writeToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUMMER_NOT_FOUND"));

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + writeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"delete\",\"email\":\"" + email + "\",\"displayName\":\"Recreated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("delete"));
        mockMvc.perform(get("/api/customers/{username}", "delete")
                        .header("Authorization", "Bearer " + readToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Recreated"));
    }

    @Test
    void returnsNotFoundForMissingCustomer() throws Exception {
        String token = accessToken("customer.read");

        mockMvc.perform(get("/api/customers/{username}", "missing")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SUMMER_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidCustomerPathThroughTheParameterContract() throws Exception {
        String token = accessToken("customer.read");

        mockMvc.perform(get("/api/customers/{username}", "x")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUMMER_INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.error.violations[0].field").value("username"));

        String writeToken = accessToken("customer.write");
        mockMvc.perform(get("/api/customers/{username}", "demo@summer.local")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.violations[0].field").value("username"));
        mockMvc.perform(patch("/api/customers/{username}", "demo@summer.local")
                        .header("Authorization", "Bearer " + writeToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.violations[0].field").value("username"));
        mockMvc.perform(delete("/api/customers/{username}", "demo@summer.local")
                        .header("Authorization", "Bearer " + writeToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.violations[0].field").value("username"));
    }

    @Test
    void enforcesThePublishedCustomerUsernameBoundaryOnCreateAndPath() throws Exception {
        String writeToken = accessToken("customer.write");
        String readToken = accessToken("customer.read");
        String[] valid = {"a.b", "a_b", "a-b", "a..b", "a" + "b".repeat(62) + "c"};
        for (int i = 0; i < valid.length; i++) {
            String username = valid[i];
            mockMvc.perform(post("/api/customers")
                            .header("Authorization", "Bearer " + writeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"email\":\"contract93-" + i + "@summer.local\",\"displayName\":\" Name \"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username").value(username))
                    .andExpect(jsonPath("$.data.displayName").value(" Name "));
            mockMvc.perform(get("/api/customers/{username}", username)
                            .header("Authorization", "Bearer " + readToken))
                    .andExpect(status().isOk());
        }

        String[] invalid = {"ab", "a" + "b".repeat(63) + "c", "Abc", "aBc", ".ab", "_ab", "-ab", "ab.", "ab_", "ab-", "a b", "a@b"};
        for (int i = 0; i < invalid.length; i++) {
            String username = invalid[i];
            mockMvc.perform(post("/api/customers")
                            .header("Authorization", "Bearer " + writeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"email\":\"invalid93-" + i + "@summer.local\",\"displayName\":\"Name\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.violations[0].field").value("username"));
        }
    }

    @Test
    void returnsStableUnauthorizedAndValidationResponses() throws Exception {
        mockMvc.perform(get("/api/customers/{username}", "summer.demo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SUMMER_UNAUTHORIZED"));

        String readOnlyToken = accessToken("customer.read");
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + readOnlyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new\",\"email\":\"new@summer.local\",\"displayName\":\"New Customer\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));

        String token = accessToken("customer.write");
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"invalid\",\"email\":\"not-an-email\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SUMMER_INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.error.violations.length()").value(2));
    }

    @Test
    void enforcesCustomerReadAndWriteScopesIndependently() throws Exception {
        String writeOnlyToken = accessToken("customer.write");
        mockMvc.perform(get("/api/customers/{username}", "summer.demo")
                        .header("Authorization", "Bearer " + writeOnlyToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));

        String readOnlyToken = accessToken("customer.read");
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + readOnlyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"scope-check\",\"email\":\"scope-check@summer.local\",\"displayName\":\"Scope Check\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void exposesHealthAndMetrics() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        MvcResult result = mockMvc.perform(get("/actuator/metrics").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject("monitoring-e2e")
                        .claim("active_enterprise_id", "ent-acme")
                        .claim("enterprise_memberships", java.util.List.of(java.util.Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.capabilities.read"))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("names");
    }

    @Test
    void exposesOidcDiscoveryAndJwkSetWithoutResponseWrapping() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://127.0.0.1:8080"))
                .andExpect(jsonPath("$.token_endpoint").value("http://127.0.0.1:8080/oauth2/token"))
                .andExpect(jsonPath("$.jwks_uri").value("http://127.0.0.1:8080/oauth2/jwks"))
                .andExpect(jsonPath("$.success").doesNotExist());

        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").isString())
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void leavesPublicApplicationEndpointAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/public/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.application").value("summer-sample"))
                .andExpect(jsonPath("$.data.status").value("ready"));
    }

    @Test
    void leavesLoginSuccessLandingEndpointAccessible() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.application").value("summer-sample"))
                .andExpect(jsonPath("$.data.status").value("ready"));
    }

    @Test
    void rejectsInvalidOAuthClientWithNativeProtocolError() throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(CLIENT_ID, "wrong-secret"))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "customer.read"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    private String accessToken(String scope) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", scope))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.access_token");
    }

    private long activeCustomerCount() {
        return sqlTemplate.count(
                dataProperties.sqlResource(),
                "countActive",
                Map.of(),
                Map.of()
        );
    }
}
