package com.abluepoint.summer.sample.identity;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class CapabilityControllerTest {

    private static final String CLIENT_ID = "summer-client";
    private static final String CLIENT_SECRET = "summer-client-demo-only";

    @Autowired
    MockMvc mockMvc;

    @Test
    void returnsPlatformCapabilitySetAndSupportsConditionalRevalidation() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/v1/platform/capabilities")
                        .with(jwt().jwt(token -> token
                                .subject("platform-operator")
                                .claim("platform_capabilities", List.of(
                                        "platform.enterprise-review.read",
                                        "platform.enterprise-review.approve"
                                )))
                                .authorities(new SimpleGrantedAuthority("SCOPE_capabilities.platform.read")))
                        .header("X-Request-Id", "platform-capabilities-request"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "platform-capabilities-request"))
                .andExpect(header().string("ETag", org.hamcrest.Matchers.startsWith("\"")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.workspace").value("platform"))
                .andExpect(jsonPath("$.data.enterpriseId").doesNotExist())
                .andExpect(jsonPath("$.data.capabilities")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                "platform.enterprise-review.read",
                                "platform.enterprise-review.approve"
                        )))
                .andExpect(jsonPath("$.data.revision").isString())
                .andReturn();

        String etag = first.getResponse().getHeader("ETag");
        mockMvc.perform(get("/api/v1/platform/capabilities")
                        .with(jwt().jwt(token -> token
                                .subject("platform-operator")
                                .claim("platform_capabilities", List.of(
                                        "platform.enterprise-review.read",
                                        "platform.enterprise-review.approve"
                                )))
                                .authorities(new SimpleGrantedAuthority("SCOPE_capabilities.platform.read")))
                        .header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", etag));
    }

    @Test
    void returnsEnterpriseCapabilitySetOnlyForTheExplicitActiveEnterpriseContext() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/capabilities", "ent-acme")
                        .with(jwt().jwt(token -> token
                                .subject("alice")
                                .claim("enterprise_memberships", List.of(
                                        Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")
                                ))
                                .claim("enterprise_capabilities", Map.of(
                                        "ent-acme", List.of("enterprise.members.read", "enterprise.members.update")
                                )))
                                .authorities(new SimpleGrantedAuthority("SCOPE_capabilities.enterprise.read")))
                        .header("X-Request-Id", "enterprise-capabilities-request"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "enterprise-capabilities-request"))
                .andExpect(header().string("ETag", org.hamcrest.Matchers.startsWith("\"")))
                .andExpect(jsonPath("$.data.workspace").value("enterprise"))
                .andExpect(jsonPath("$.data.enterpriseId").value("ent-acme"))
                .andExpect(jsonPath("$.data.capabilities[1]").value("enterprise.members.update"));
    }

    @Test
    void deniesAnEnterpriseContextThatIsNotAnActiveMembership() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/{enterpriseId}/capabilities", "ent-other")
                        .with(jwt().jwt(token -> token
                                .subject("alice")
                                .claim("enterprise_memberships", List.of(
                                        Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")
                                )))
                                .authorities(new SimpleGrantedAuthority("SCOPE_capabilities.enterprise.read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void returnsUnauthorizedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/platform/capabilities"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SUMMER_UNAUTHORIZED"));
    }

    @Test
    void returnsForbiddenWhenAuthenticatedPrincipalLacksPlatformCapabilityAuthority() throws Exception {
        mockMvc.perform(get("/api/v1/platform/capabilities")
                        .with(jwt().jwt(token -> token.subject("ordinary-user"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void acceptsTheConfiguredPlatformCapabilityScopeFromTheAuthorizationServer() throws Exception {
        String token = accessToken("capabilities.platform.read");

        mockMvc.perform(get("/api/v1/platform/capabilities")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspace").value("platform"))
                .andExpect(jsonPath("$.data.capabilities[0]").value("platform.capabilities.read"));
    }

    private String accessToken(String scope) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", scope))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.access_token");
    }
}
