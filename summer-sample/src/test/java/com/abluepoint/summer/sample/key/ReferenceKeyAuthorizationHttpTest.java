package com.abluepoint.summer.sample.key;

import com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture;
import com.abluepoint.summer.sample.authorization.api.permission.InterfacePermissionController;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "summer.security.storage.keys=reference",
        "summer.sample.security.key-reference.source=environment",
        "summer.sample.security.key-reference.name=PERM14_REFERENCE_KEY_MATERIAL",
        "summer.sample.security.key-reference.kid=perm14-reference"})
@AutoConfigureMockMvc
@Import(CurrentManagedAuthorizationTestFixture.class)
class ReferenceKeyAuthorizationHttpTest {
    private static final String SUBJECT = "reference-key-operator";
    @Autowired MockMvc http;
    @Autowired CurrentManagedAuthorizationTestFixture current;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping handlers;

    @DynamicPropertySource
    static void referenceMaterial(DynamicPropertyRegistry properties) throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String material = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        properties.add("PERM14_REFERENCE_KEY_MATERIAL", () -> material);
    }

    @Test
    void actualReferenceMetadataUsesFormalCurrentBindingsAndKeepsJwksPublic() throws Exception {
        assertThat(handlers.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getKey().getPatternValues().contains("/api/security/keys"))
                .map(entry -> entry.getValue().getBeanType().getName()).toList())
                .containsExactly(ReferenceKeyCapabilityController.class.getName());
        var actor = actor("security.key.read platform.capabilities.read platform.capabilities.write");
        String permission = current.grantHandlers(Domain.platform(), SUBJECT, (handler, path, method) ->
                handler.getBeanType() == InterfacePermissionController.class
                        && java.util.Set.of("GET", "PUT").contains(method)
                        && java.util.Set.of("/api/v1/platform/authorization/interface-directory",
                                "/api/v1/platform/permissions/{permissionId}/interfaces").contains(path));
        http.perform(get("/oauth2/jwks")).andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid").value("perm14-reference"));
        http.perform(get("/api/security/keys").with(actor)).andExpect(status().isForbidden());

        var resources = new ArrayList<Map<String, Object>>();
        for (int page = 0; ; page++) {
            var response = http.perform(get("/api/v1/platform/authorization/interface-directory")
                            .queryParam("page", Integer.toString(page)).queryParam("size", "200").with(actor))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Map<String, Object>> items = JsonPath.read(response, "$.data.items");
            resources.addAll(items);
            Number total = JsonPath.read(response, "$.data.total");
            if (resources.size() >= total.longValue()) break;
            assertThat(items).isNotEmpty();
        }
        var keys = resources.stream().filter(item -> "/api/security/keys".equals(item.get("normalizedPath"))).toList();
        assertThat(keys).hasSize(2);
        assertThat(keys.stream().map(item -> item.get("httpMethod"))).containsExactlyInAnyOrder("GET", "HEAD");
        String bindingPath = "/api/v1/platform/permissions/" + permission + "/interfaces";
        var binding = http.perform(get(bindingPath).with(actor)).andExpect(status().isOk()).andReturn();
        List<String> original = JsonPath.read(binding.getResponse().getContentAsString(), "$.data.resourceIds");
        var combined = new ArrayList<>(original);
        keys.forEach(item -> combined.add(item.get("resourceId").toString()));
        var granted = http.perform(put(bindingPath).with(actor).header("If-Match", binding.getResponse().getHeader("ETag"))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("resourceIds", combined))))
                .andExpect(status().isOk()).andReturn();
        http.perform(get("/api/security/keys").with(actor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].source").value("environment"))
                .andExpect(jsonPath("$.data.items[0].health").value("READY"));
        http.perform(head("/api/security/keys").with(actor)).andExpect(status().isOk());
        http.perform(get("/api/security/keys").with(actor("openid"))).andExpect(status().isForbidden());
        http.perform(put(bindingPath).with(actor).header("If-Match", granted.getResponse().getHeader("ETag"))
                        .header("Idempotency-Key", UUID.randomUUID()).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("resourceIds", original))))
                .andExpect(status().isOk());
        http.perform(get("/api/security/keys").with(actor)).andExpect(status().isForbidden());
        http.perform(head("/api/security/keys").with(actor)).andExpect(status().isForbidden());
        http.perform(get("/oauth2/jwks")).andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid").value("perm14-reference"));
    }

    private static RequestPostProcessor actor(String scope) {
        return jwt().jwt(claims -> claims.subject(SUBJECT).claim("scope", scope))
                .authorities(new SimpleGrantedAuthority("SCOPE_security.key.read"));
    }
}
