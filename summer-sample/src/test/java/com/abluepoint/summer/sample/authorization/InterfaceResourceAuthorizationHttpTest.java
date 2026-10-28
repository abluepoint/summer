package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.authorization.application.AuthorizationService;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationCapabilityEntity;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationCapabilityRepository;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationRoleEntity;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationRoleRepository;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationUserBindingEntity;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationUserBindingRepository;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InterfaceResourceAuthorizationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired AuthorizationCapabilityRepository capabilities;
    @Autowired AuthorizationRoleRepository roles;
    @Autowired AuthorizationUserBindingRepository bindings;
    @Autowired AuthorizationService authorization;

    @Test
    void scannedTemplateGrantAppliesToDifferentResourcePathsAndTemplateDenyWins() throws Exception {
        String permission = scannedPermission("/api/customers/{username}");
        assertThat(permission).isEqualTo("GET:/api/customers/{username}");
        assign(permission);

        // These absent Customers reach the handler (404), instead of being rejected by authorization (403).
        mvc.perform(get("/api/customers/missing-one").with(member("customer.read"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/customers/missing-two").with(member("customer.read"))).andExpect(status().isNotFound());
        assertThat(authorization.audit("ent-acme", "resource-reader", auditor()))
                .anySatisfy(row -> assertThat(row).containsEntry("permissionCode", permission).containsEntry("decision", "ALLOW"));

        bindings.findByEnterpriseIdAndUsername("ent-acme", "resource-reader").ifPresent(binding -> {
            binding.update("[\"resource-role\"]", "[\"" + permission + "\"]", 2);
            bindings.saveAndFlush(binding);
        });
        mvc.perform(get("/api/customers/missing-one").with(member("customer.read"))).andExpect(status().isForbidden());
        assertThat(authorization.audit("ent-acme", "resource-reader", auditor()))
                .anySatisfy(row -> assertThat(row).containsEntry("permissionCode", permission)
                        .containsEntry("reasonCode", "USER_EXPLICIT_DENY"));
    }

    @Test
    void explicitAnnotationRemainsAuthoritativeForManagedGrant() throws Exception {
        String permission = scannedPermission("/api/v1/platform/interface-resources");
        assertThat(permission).isEqualTo("platform.interface-resources.read");
        assign(permission);
        mvc.perform(get("/api/v1/platform/interface-resources")
                        .with(jwt().jwt(token -> token.subject("resource-reader")
                                        .claim("enterprise_memberships", memberships())
                                        .claim("platform_capabilities", List.of(permission)))
                                .authorities(new SimpleGrantedAuthority("SCOPE_" + permission))))
                .andExpect(status().isOk());
    }

    private String scannedPermission(String path) throws Exception {
        var response = mvc.perform(get("/api/v1/platform/interface-resources")
                        .with(jwt().jwt(token -> token.subject("catalog-reader")
                                        .claim("platform_capabilities", List.of("platform.interface-resources.read")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_platform.interface-resources.read"))))
                .andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> resources = JsonPath.read(response.getResponse().getContentAsString(), "$.data.items");
        return resources.stream().filter(row -> path.equals(row.get("normalizedPath")) && "GET".equals(row.get("httpMethod")))
                .map(row -> String.valueOf(row.get("permissionCode"))).findFirst().orElseThrow();
    }

    private void assign(String permission) {
        capabilities.saveAndFlush(new AuthorizationCapabilityEntity("resource-cap", "ent-acme", "ENTERPRISE",
                permission, permission, "invoke", "NORMAL", "ACTIVE", 1));
        roles.saveAndFlush(new AuthorizationRoleEntity("resource-role", "ent-acme", "Resource reader", "resource-reader",
                false, "ACTIVE", "resource-cap", permission, null, 1));
        bindings.saveAndFlush(new AuthorizationUserBindingEntity("resource-binding", "ent-acme", "resource-reader",
                "[\"resource-role\"]", "[]", 1));
    }

    private RequestPostProcessor member(String scope) {
        return jwt().jwt(token -> token.subject("resource-reader").claim("enterprise_memberships", memberships()))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
    }

    private org.springframework.security.core.Authentication auditor() {
        var token = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test").header("alg", "none")
                .subject("audit-reader").claim("enterprise_memberships", memberships())
                .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.roles.read"))).build();
        return new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(token,
                List.of(new SimpleGrantedAuthority("SCOPE_enterprise.roles.read")));
    }

    private static List<Map<String, String>> memberships() {
        return List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"));
    }
}
