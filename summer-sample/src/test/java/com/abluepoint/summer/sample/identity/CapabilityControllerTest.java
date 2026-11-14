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
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class CapabilityControllerTest {

    private static final String CLIENT_ID = "summer-client";
    private static final String CLIENT_SECRET = "summer-client-demo-only";

    @Autowired
    MockMvc mockMvc;

    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    String platformPermission, enterprisePermission;

    @org.junit.jupiter.api.BeforeEach
    void currentProjectionReplacements() {
        for (String subject : List.of("platform-operator", CLIENT_ID)) {
            String id = current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), subject,
                    (handler, path, method) -> method.equals("GET") && (
                            handler.getBeanType() == CapabilityController.class
                            || handler.getBeanType().getName().equals("com.abluepoint.summer.sample.authorization.api.permission.PermissionController")
                            || handler.getBeanType().getName().equals("com.abluepoint.summer.sample.resource.InterfaceResourceCatalogController")));
            if (subject.equals("platform-operator")) platformPermission = id;
        }
        enterprisePermission = current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"), "alice",
                (handler, path, method) -> method.equals("GET") && (handler.getBeanType() == CapabilityController.class
                        || handler.getBeanType().getName().equals("com.abluepoint.summer.sample.authorization.api.permission.PermissionController")));
    }

    @Test
    void managedPlatformUsesCurrentPermissionsAndRevalidatesTheCurrentCatalog() throws Exception {
        var auth = jwt().jwt(claims -> claims.subject("platform-operator")
                        .claim("platform_capabilities", List.of("platform.enterprise-review.approve"))
                        .claim("scope", "platform.capabilities.read platform.interface-resources.read capabilities.platform.read"))
                .authorities(new SimpleGrantedAuthority("SCOPE_capabilities.platform.read"),
                        new SimpleGrantedAuthority("SCOPE_platform.capabilities.read"),new SimpleGrantedAuthority("SCOPE_platform.interface-resources.read"));
        mockMvc.perform(get("/api/v1/platform/capabilities").with(auth).header("X-Request-Id", "platform-capabilities-request"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SUMMER_CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("Legacy capability projections are unavailable in managed mode; use current permission/role access"))
                .andExpect(jsonPath("$.requestId").value("platform-capabilities-request"))
                .andExpect(header().string("X-Request-Id", "platform-capabilities-request"))
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(get("/api/v1/platform/permissions/{id}",platformPermission).with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.permissionId").value(platformPermission));
        var first = mockMvc.perform(get("/api/v1/platform/interface-resources").with(auth))
                .andExpect(status().isOk()).andExpect(header().string("ETag",org.hamcrest.Matchers.startsWith("\"")))
                .andReturn();
        mockMvc.perform(get("/api/v1/platform/interface-resources").with(auth).header("If-None-Match",first.getResponse().getHeader("ETag")))
                .andExpect(status().isNotModified()).andExpect(header().string("ETag",first.getResponse().getHeader("ETag")));
    }

    @Test
    void managedEnterpriseRetiresTheOldProjectionAndReadsItsCurrentPermissionFacts() throws Exception {
        var auth = jwt().jwt(claims -> claims.subject("alice").claim("scope","enterprise.roles.read capabilities.enterprise.read")
                        .claim("active_enterprise_id","ent-acme")
                        .claim("enterprise_memberships",List.of(Map.of("enterpriseId","ent-acme","status","ACTIVE")))
                        .claim("enterprise_capabilities",Map.of("ent-acme",List.of("enterprise.members.update"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_capabilities.enterprise.read"),new SimpleGrantedAuthority("SCOPE_enterprise.roles.read"));
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/capabilities").with(auth).header("X-Request-Id","enterprise-capabilities-request"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SUMMER_CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("Legacy capability projections are unavailable in managed mode; use current permission/role access"))
                .andExpect(jsonPath("$.requestId").value("enterprise-capabilities-request"))
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/permissions/{id}",enterprisePermission).with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.permissionId").value(enterprisePermission));
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

    @Autowired
    org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository clients;

    @Test
    void registeredPlatformScopeIsIssuedButCannotRestoreTheRetiredCapabilityProjection() throws Exception {
        var original = clients.findByClientId(CLIENT_ID);
        clients.save(org.springframework.security.oauth2.server.authorization.client.RegisteredClient.from(original)
                .scope("capabilities.platform.read").build());
        String token;
        try {
            token = accessToken("capabilities.platform.read");
        } finally {
            clients.save(original);
        }

        var claims = new tools.jackson.databind.ObjectMapper().readTree(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        var rawScope = claims.path("scope");
        org.assertj.core.api.Assertions.assertThat(rawScope.isArray()
                ? java.util.stream.StreamSupport.stream(rawScope.spliterator(),false).anyMatch(value -> value.asText().equals("capabilities.platform.read"))
                : java.util.Arrays.asList(rawScope.asText().split(" ")).contains("capabilities.platform.read")).isTrue();
        mockMvc.perform(get("/api/v1/platform/capabilities")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-Id", "registered-capability-scope"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("SUMMER_CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("Legacy capability projections are unavailable in managed mode; use current permission/role access"))
                .andExpect(jsonPath("$.requestId").value("registered-capability-scope"));
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
