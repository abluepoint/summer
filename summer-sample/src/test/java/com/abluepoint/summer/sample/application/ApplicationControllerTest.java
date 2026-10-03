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
