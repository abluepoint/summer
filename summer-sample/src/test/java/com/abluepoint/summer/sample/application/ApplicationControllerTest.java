package com.abluepoint.summer.sample.application;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApplicationControllerTest {
    @Autowired MockMvc mockMvc;

    @Test void nativeAccessDenialsKeepTheirOriginalMessageAndCause() throws Exception {
        String path = "/api/v1/enterprises/ent-acme/applications/missing";
        MvcResult result = mockMvc.perform(get(path).with(jwt().jwt(t -> t.subject("application-native-no-cap").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))).header("X-Request-Id", "native-denied"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Application capability denied"))
                .andExpect(jsonPath("$.requestId").value("native-denied"))
                .andExpect(header().string("X-Request-Id", "native-denied"))
                .andExpect(jsonPath("$.error.cause").doesNotExist()).andReturn();
        var error = org.junit.jupiter.api.Assertions.assertInstanceOf(com.abluepoint.summer.common.error.SummerException.class,
                result.getResolvedException());
        var cause = org.junit.jupiter.api.Assertions.assertInstanceOf(org.springframework.security.access.AccessDeniedException.class,
                error.getCause());
        org.junit.jupiter.api.Assertions.assertEquals(cause.getMessage(), error.explicitMessage());
    }

    @Test void rejectedApplicationMutationsKeepTheirMessagesAndDoNotChangeCredentialsOrVersion() throws Exception {
        String base = "/api/v1/enterprises/ent-acme/applications";
        failure(post(base).contentType(MediaType.APPLICATION_JSON).content("{}"), 400,
                "SUMMER_APPLICATION_INVALID", "Idempotency-Key is required");
        failure(post(base).contentType(MediaType.APPLICATION_JSON).content("{}").header("Idempotency-Key", "empty"), 400,
                "SUMMER_APPLICATION_INVALID", "name is required");
        failure(post(base).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"CRM\",\"redirectUris\":[\"https://example.test/*\"]}")
                .header("Idempotency-Key", "redirect"), 400, "SUMMER_APPLICATION_INVALID", "Redirect URI must be exact and use https");
        MvcResult created = create("app-stable");
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.data.applicationId");
        String path = base + "/" + id;
        failure(post(base).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Changed\"}")
                .header("Idempotency-Key", "app-stable"), 409, "SUMMER_IDEMPOTENCY_CONFLICT", "Idempotency key reused");
        failure(get(base + "/missing"), 409, "SUMMER_APPLICATION_CONFLICT", "Application not found");
        failure(patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Changed\"}")
                .header("If-Match", "\"999\"").header("Idempotency-Key", "stale"), 412,
                "SUMMER_PRECONDITION_FAILED", "The resource has changed");
        failure(patch(path).contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("If-Match", "\"1\"").header("Idempotency-Key", "empty-update"), 400,
                "SUMMER_APPLICATION_INVALID", "name is required");
        failure(put(path + "/oauth-config").contentType(MediaType.APPLICATION_JSON).content("{\"grantTypes\":[\"implicit\"]}")
                .header("If-Match", "\"1\"").header("Idempotency-Key", "unsafe"), 400,
                "SUMMER_APPLICATION_INVALID", "Unsafe grant type");
        failure(put(path + "/oauth-config").contentType(MediaType.APPLICATION_JSON)
                .content("{\"grantTypes\":[\"authorization_code\"],\"clientAuthMethod\":\"none\",\"pkceRequired\":false}")
                .header("If-Match", "\"1\"").header("Idempotency-Key", "pkce"), 400,
                "SUMMER_APPLICATION_INVALID", "PKCE is required");
        failure(post(path + "/credentials/missing:revoke").header("If-Match", "\"1\"").header("Idempotency-Key", "missing-credential"),
                409, "SUMMER_APPLICATION_CONFLICT", "Credential not found");
        mockMvc.perform(get(path).with(admin("ent-acme", "enterprise.application.read")))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.data.name").value("CRM"))
                .andExpect(jsonPath("$.data.credentials.length()").value(1))
                .andExpect(jsonPath("$.data.credentials[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.clientSecret").doesNotExist());
        mockMvc.perform(get(path + "/audit").with(admin("ent-acme", "enterprise.application.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].action").value("APPLICATION_CREATED"));
    }

    private void failure(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                         int status, String code, String message) throws Exception {
        mockMvc.perform(request.with(jwt().jwt(token -> token.subject("admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.application.read", "enterprise.application.write"))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.application.read"),
                                new SimpleGrantedAuthority("SCOPE_enterprise.application.write")))
                        .header("X-Request-Id", "app-rejected"))
                .andExpect(status().is(status)).andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message").value(message))
                .andExpect(jsonPath("$.requestId").value("app-rejected"))
                .andExpect(header().string("X-Request-Id", "app-rejected"))
                .andExpect(jsonPath("$.error.cause").doesNotExist());
    }

    @Test void createsListsAndReturnsSecretOnlyOnce() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/enterprises/ent-acme/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"CRM\",\"description\":\"Customer app\",\"redirectUris\":[\"https://crm.example.test/callback\"]}")
                .header("Idempotency-Key", "app-create-1").with(admin("ent-acme", "enterprise.application.write")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.applicationId").isNotEmpty())
                .andExpect(jsonPath("$.data.clientId").isNotEmpty()).andExpect(jsonPath("$.data.clientSecret").isNotEmpty()).andReturn();
        String appId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.applicationId");
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/applications").with(admin("ent-acme", "enterprise.application.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].applicationId").value(appId));
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/applications/" + appId).with(admin("ent-acme", "enterprise.application.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.clientSecret").doesNotExist());
    }

    @Test void validatesOauthAndRedirectsExactly() throws Exception {
        MvcResult created = create("oauth-1");
        String appId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.applicationId");
        String etag = created.getResponse().getHeader("ETag");
        mockMvc.perform(put("/api/v1/enterprises/ent-acme/applications/" + appId + "/oauth-config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grantTypes\":[\"authorization_code\"],\"responseTypes\":[\"code\"],\"scopes\":[\"openid\",\"profile\"],\"clientAuthMethod\":\"none\",\"pkceRequired\":true,\"redirectUris\":[\"https://crm.example.test/callback\"]}")
                .header("If-Match", etag).header("Idempotency-Key", "oauth-config-1").with(admin("ent-acme", "enterprise.application.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pkceRequired").value(true));
        mockMvc.perform(put("/api/v1/enterprises/ent-acme/applications/" + appId + "/oauth-config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grantTypes\":[\"implicit\"],\"responseTypes\":[\"token\"],\"redirectUris\":[\"https://crm.example.test/*\"]}")
                .header("If-Match", "\"2\"").header("Idempotency-Key", "oauth-invalid-1").with(admin("ent-acme", "enterprise.application.write")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("SUMMER_APPLICATION_INVALID"));
    }

    @Test void rotatesAndRevokesCredentialWithCacheInvalidation() throws Exception {
        MvcResult created = create("cred-1");
        String appId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.applicationId");
        String firstSecret = JsonPath.read(created.getResponse().getContentAsString(), "$.data.clientSecret");
        String etag = created.getResponse().getHeader("ETag");
        MvcResult rotated = mockMvc.perform(post("/api/v1/enterprises/ent-acme/applications/" + appId + "/credentials:rotate")
                .header("If-Match", etag).header("Idempotency-Key", "rotate-1").with(admin("ent-acme", "enterprise.application.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.clientSecret").isNotEmpty()).andReturn();
        String secondSecret = JsonPath.read(rotated.getResponse().getContentAsString(), "$.data.clientSecret");
        org.junit.jupiter.api.Assertions.assertNotEquals(firstSecret, secondSecret);
        String credentialId = JsonPath.read(rotated.getResponse().getContentAsString(), "$.data.credentialId");
        mockMvc.perform(post("/api/v1/enterprises/ent-acme/applications/" + appId + "/credentials/" + credentialId + ":revoke")
                .header("If-Match", rotated.getResponse().getHeader("ETag")).header("Idempotency-Key", "revoke-1").with(admin("ent-acme", "enterprise.application.write")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    @Test void isolatesTenantAndRequiresActiveMembershipCapabilityAndScope() throws Exception {
        mockMvc.perform(get("/api/v1/enterprises/ent-other/applications").with(admin("ent-acme", "enterprise.application.read")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/enterprises/ent-acme/applications").with(jwt().jwt(t -> t.subject("x")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.application.read"))))
                .andExpect(status().isForbidden());
    }

    private MvcResult create(String key) throws Exception {
        return mockMvc.perform(post("/api/v1/enterprises/ent-acme/applications").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"CRM\",\"redirectUris\":[\"https://crm.example.test/callback\"]}")
                .header("Idempotency-Key", key).with(admin("ent-acme", "enterprise.application.write")))
                .andExpect(status().isCreated()).andReturn();
    }
    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(String enterpriseId, String capability) {
        return jwt().jwt(token -> token.subject("enterprise-admin")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", enterpriseId, "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of(enterpriseId, List.of(capability))))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + capability));
    }
}
