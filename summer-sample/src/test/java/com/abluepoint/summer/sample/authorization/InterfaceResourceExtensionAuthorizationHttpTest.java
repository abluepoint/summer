package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationCapabilityEntity;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationCapabilityRepository;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationRoleEntity;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationRoleRepository;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationUserBindingEntity;
import com.abluepoint.summer.sample.authorization.infrastructure.persistence.AuthorizationUserBindingRepository;

import com.example.catalog.CatalogExtensionController;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(CatalogExtensionController.class)
@DirtiesContext
class InterfaceResourceExtensionAuthorizationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired AuthorizationCapabilityRepository capabilities;
    @Autowired AuthorizationRoleRepository roles;
    @Autowired AuthorizationUserBindingRepository bindings;

    @Test void newPackageUsesScannedPermissionForManagedGrantAndExplicitDeny() throws Exception {
        var response = mvc.perform(get("/api/v1/platform/interface-resources").with(jwt()
                .jwt(token -> token.subject("catalog-reader").claim("platform_capabilities", List.of("platform.interface-resources.read")))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.interface-resources.read"))))
                .andExpect(status().isOk()).andReturn();
        List<Map<String,Object>> rows = JsonPath.read(response.getResponse().getContentAsString(), "$.data.items");
        String permission = rows.stream().filter(row -> "/api/catalog-extension".equals(row.get("normalizedPath")) && "GET".equals(row.get("httpMethod")))
                .map(row -> String.valueOf(row.get("permissionCode"))).findFirst().orElseThrow();
        assertThat(permission).isEqualTo("GET:/api/catalog-extension");
        capabilities.saveAndFlush(new AuthorizationCapabilityEntity("extension-cap", "ent-acme", "ENTERPRISE", permission, permission, "invoke", "NORMAL", "ACTIVE", 1));
        roles.saveAndFlush(new AuthorizationRoleEntity("extension-role", "ent-acme", "Extension", "extension", false, "ACTIVE", "extension-cap", permission, null, 1));
        var binding = bindings.saveAndFlush(new AuthorizationUserBindingEntity("extension-binding", "ent-acme", "extension-reader", "[]", "[]", 1));
        var member = jwt().jwt(token -> token.subject("extension-reader").claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))));
        mvc.perform(get("/api/catalog-extension").param("mode", "one").with(member)).andExpect(status().isForbidden());
        binding.update("[\"extension-role\"]", "[]", 2); bindings.saveAndFlush(binding);
        mvc.perform(get("/api/catalog-extension").param("mode", "one").with(member)).andExpect(status().isOk());
        mvc.perform(get("/api/catalog-extension").param("mode", "two").with(member)).andExpect(status().isOk());
        binding.update("[\"extension-role\"]", "[\"" + permission + "\"]", 3); bindings.saveAndFlush(binding);
        mvc.perform(get("/api/catalog-extension").param("mode", "one").with(member)).andExpect(status().isForbidden());
    }
}
