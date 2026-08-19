package com.abluepoint.summer.sample;

import com.abluepoint.summer.data.jpa.template.JpaSqlTemplate;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class SummerEndToEndTest {

    private static final String CLIENT_ID = "summer-client";
    private static final String CLIENT_SECRET = "summer-client-demo-only";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JpaSqlTemplate sqlTemplate;

    @Test
    void issuesTokenAndUsesItForProtectedTemplateQuery() throws Exception {
        String token = accessToken("customer.read");

        mockMvc.perform(get("/api/customers/{email}", "demo@summer.local")
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
                        .content("{\"email\":\"created@summer.local\",\"displayName\":\"Created Customer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("created@summer.local"))
                .andExpect(jsonPath("$.data.displayName").value("Created Customer"));

        assertThat(activeCustomerCount()).isEqualTo(countBefore + 1);

        String readToken = accessToken("customer.read");
        mockMvc.perform(get("/api/customers/{email}", "created@summer.local")
                        .header("Authorization", "Bearer " + readToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("created@summer.local"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void rejectsDuplicateCustomerWithConflictResponse() throws Exception {
        String token = accessToken("customer.write");

        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"demo@summer.local\",\"displayName\":\"Duplicate\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SUMMER_CONFLICT"));
    }

    @Test
    void returnsNotFoundForMissingCustomer() throws Exception {
        String token = accessToken("customer.read");

        mockMvc.perform(get("/api/customers/{email}", "missing@summer.local")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SUMMER_NOT_FOUND"));
    }

    @Test
    void returnsStableUnauthorizedAndValidationResponses() throws Exception {
        mockMvc.perform(get("/api/customers/{email}", "demo@summer.local"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SUMMER_UNAUTHORIZED"));

        String readOnlyToken = accessToken("customer.read");
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + readOnlyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@summer.local\",\"displayName\":\"New Customer\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));

        String token = accessToken("customer.write");
        mockMvc.perform(post("/api/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SUMMER_INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.error.violations.length()").value(2));
    }

    @Test
    void exposesHealthAndMetrics() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        MvcResult result = mockMvc.perform(get("/actuator/metrics"))
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
                "classpath:sql/customer-queries.sql",
                "countActive",
                Map.of(),
                Map.of()
        );
    }
}
