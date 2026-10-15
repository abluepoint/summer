package com.abluepoint.summer.sample.identity;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:authorization-bootstrap;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "summer.sample.authorization.legacy-scope-compatibility=false"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class AuthorizationBootstrapHttpTest {
    @Autowired MockMvc mvc;
    @Autowired com.abluepoint.summer.security.autoconfigure.SummerSecurityProperties properties;

    @Test
    void bootstrapAdminCanAssignPersistedManagementRightsAndManageAfterFreshPkceLogin() throws Exception {
        // Bootstrap authority belongs to the configured first administrator, not every ADMIN account.
        String username = properties.getAuthorizationServer().getUser().getUsername();
        String password = properties.getAuthorizationServer().getUser().getPassword();
        String bootstrap = login(username, password);

        MvcResult catalog = mvc.perform(get("/api/v1/platform/interface-resources")
                        .header("Authorization", "Bearer " + bootstrap))
                .andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> resources = JsonPath.read(catalog.getResponse().getContentAsString(), "$.data.items");
        assertThat(resources).anySatisfy(resource -> {
            assertThat(resource.get("normalizedPath")).isEqualTo("/api/v1/enterprises/{enterpriseId}/roles");
            assertThat(resource.get("httpMethod")).isEqualTo("GET");
            assertThat(resource.get("permissionCode")).isEqualTo("enterprise.roles.read");
        });
        assertBusinessDenied(bootstrap);

        String roleId = createManagementRole(bootstrap, "Initial");
        mvc.perform(put("/api/v1/enterprises/ent-acme/users/{username}/roles", username)
                        .header("Authorization", "Bearer " + bootstrap)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[\"" + roleId + "\"],\"deniedCapabilityCodes\":[]}"))
                .andExpect(status().isOk());

        String managed = login(username, password);
        Map<String, Object> claims = JsonPath.read(new String(Base64.getUrlDecoder().decode(managed.split("\\.")[1]),
                StandardCharsets.UTF_8), "$");
        assertThat(claims.get("summer_managed_grants")).isEqualTo(true);
        assertThat(JsonPath.<List<String>>read(claims, "$.enterprise_capabilities.ent-acme"))
                .containsExactlyInAnyOrder("enterprise.roles.read", "enterprise.roles.write");
        assertThat(JsonPath.<List<String>>read(claims, "$.platform_capabilities"))
                .containsExactly("platform.interface-resources.read");

        mvc.perform(get("/api/v1/enterprises/ent-acme/roles").header("Authorization", "Bearer " + managed))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/platform/interface-resources").header("Authorization", "Bearer " + managed))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/enterprises/ent-acme/users/{username}/roles", username)
                        .header("Authorization", "Bearer " + managed))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.roleIds[0]").value(roleId));
        createManagementRole(managed, "AfterLogin");
        assertBusinessDenied(managed);
    }

    private String createManagementRole(String token, String name) throws Exception {
        var result = mvc.perform(post("/api/v1/enterprises/ent-acme/roles")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"code\":\"management-" + UUID.randomUUID()
                                + "\",\"capabilityIds\":[\"enterprise.roles.read\",\"enterprise.roles.write\",\"platform.interface-resources.read\"]}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.roleId");
    }

    private void assertBusinessDenied(String token) throws Exception {
        mvc.perform(get("/api/v1/enterprises/ent-acme/members").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String login(String username, String password) throws Exception {
        String verifier = UUID.randomUUID() + "-" + UUID.randomUUID();
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        var uri = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code").queryParam("client_id", "summer-ui")
                .queryParam("redirect_uri", "http://127.0.0.1:5173/oauth2/callback")
                .queryParam("scope", "openid profile").queryParam("state", UUID.randomUUID())
                .queryParam("code_challenge", challenge).queryParam("code_challenge_method", "S256")
                .build().encode().toUri();
        var initial = mvc.perform(get(uri).header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection()).andReturn();
        var session = (MockHttpSession) initial.getRequest().getSession(false);
        mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", username).param("password", password))
                .andExpect(status().is3xxRedirection());
        var callback = mvc.perform(get(uri).session(session).header("Accept", "text/html"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String code = UriComponentsBuilder.fromUriString(callback.getResponse().getHeader("Location"))
                .build().getQueryParams().getFirst("code");
        assertThat(code != null).isTrue();
        var issued = mvc.perform(post("/oauth2/token").param("grant_type", "authorization_code")
                        .param("client_id", "summer-ui").param("redirect_uri", "http://127.0.0.1:5173/oauth2/callback")
                        .param("code", code).param("code_verifier", verifier))
                .andExpect(status().isOk()).andReturn();
        session.invalidate();
        return JsonPath.read(issued.getResponse().getContentAsString(), "$.access_token");
    }
}
