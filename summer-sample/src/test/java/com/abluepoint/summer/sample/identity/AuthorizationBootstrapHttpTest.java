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
class AuthorizationBootstrapHttpTest extends IdentityHttpSupport {
    @Test
    void bootstrapAdminCanAssignPersistedManagementRightsAndManageAfterFreshPkceLogin() throws Exception {
        String username = properties.getAuthorizationServer().getUser().getUsername();
        String bootstrap = authorizeSelf(bootstrapLogin(), List.of("enterprise.roles.read", "enterprise.roles.write",
                "platform.interface-resources.read"));
        var catalog = mvc.perform(get("/api/v1/platform/interface-resources").header("Authorization", "Bearer " + bootstrap))
                .andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> resources = JsonPath.read(catalog.getResponse().getContentAsString(), "$.data.items");
        assertThat(resources).anySatisfy(resource -> {
            assertThat(resource.get("normalizedPath")).isEqualTo("/api/v1/enterprises/{enterpriseId}/roles");
            assertThat(resource.get("httpMethod")).isEqualTo("GET");
            assertThat(resource).doesNotContainKey("permissionCode");
            assertThat(resource.get("handler")).isNotNull();
        });
        assertBusinessDenied(bootstrap);
        String roleId = createManagementRole(bootstrap, "Initial");
        var binding = mvc.perform(get("/api/v1/enterprises/ent-acme/users/{username}/roles", username)
                        .header("Authorization", "Bearer " + bootstrap)).andExpect(status().isOk()).andReturn();
        mvc.perform(put("/api/v1/enterprises/ent-acme/users/{username}/roles", username)
                        .header("Authorization", "Bearer " + bootstrap).header("Idempotency-Key", UUID.randomUUID())
                        .header("If-Match", binding.getResponse().getHeader("ETag")).contentType(MediaType.APPLICATION_JSON)
                        .content(new tools.jackson.databind.ObjectMapper().writeValueAsString(Map.of("roleIds", List.of(roleId)))))
                .andExpect(status().isOk());
        String managed = bootstrapLogin();
        Map<String, Object> claims = JsonPath.read(new String(Base64.getUrlDecoder().decode(managed.split("\\.")[1]),
                StandardCharsets.UTF_8), "$");
        assertThat(claims.get("summer_managed_grants")).isEqualTo(true);
        assertThat((Map<?, ?>) claims.get("enterprise_capabilities")).isEmpty();
        assertThat((List<?>) claims.get("platform_capabilities")).isEmpty();
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
        String permission = identityFacts().grantHandlers(
                com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"),
                properties.getAuthorizationServer().getUser().getUsername(),
                (handler, path, method) -> handler.getBeanType().getName().equals(
                        "com.abluepoint.summer.sample.authorization.api.permission.RolePermissionController"));
        var result = mvc.perform(post("/api/v1/enterprises/ent-acme/roles").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(new tools.jackson.databind.ObjectMapper().writeValueAsString(Map.of("name", name,
                                "code", "management-" + UUID.randomUUID(), "permissionIds", List.of(permission)))))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.roleId");
    }

    private void assertBusinessDenied(String token) throws Exception {
        mvc.perform(get("/api/v1/enterprises/ent-acme/members").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
