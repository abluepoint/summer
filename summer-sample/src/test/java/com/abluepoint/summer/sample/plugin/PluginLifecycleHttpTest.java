package com.abluepoint.summer.sample.plugin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PluginLifecycleHttpTest {
    @Autowired MockMvc mockMvc;

    @Test
    void registersTrustedArtifactEnablesItAndReadsScopedExampleWithAuditBoundary() throws Exception {
        mockMvc.perform(post("/api/v1/platform/plugins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pluginId\":\"customer-insights\",\"version\":\"1.0.0\",\"artifactDigest\":\"sha256:customer-insights-v1\"}")
                        .with(platform("platform.plugins.write")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.trusted").value(true))
                .andExpect(jsonPath("$.data.status").value("REGISTERED"));

        mockMvc.perform(post("/api/v1/platform/plugins/customer-insights:enable")
                        .with(platform("platform.plugins.write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENABLED"));

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/plugins")
                        .with(enterprise("enterprise.plugins.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].pluginId").value("customer-insights"))
                .andExpect(jsonPath("$.data.items[0].menus[0].capabilityCode").value("enterprise.plugins.customer-insights.read"));

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/plugins/customer-insights/summary")
                        .with(enterprise("enterprise.plugins.customer-insights.read", "customer.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pluginId").value("customer-insights"))
                .andExpect(jsonPath("$.data.requestId").isString())
                .andExpect(jsonPath("$.data.configuration").isArray())
                .andExpect(jsonPath("$.data.dictionary.code").value("customer-segment"));
    }

    @Test
    void rejectsUntrustedArtifactAndIncompatibleUpgradeAndEnforcesDataPermission() throws Exception {
        mockMvc.perform(post("/api/v1/platform/plugins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pluginId\":\"customer-insights\",\"version\":\"1.0.0\",\"artifactDigest\":\"sha256:wrong\"}")
                        .with(platform("platform.plugins.write")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_PLUGIN_UNTRUSTED"));

        mockMvc.perform(post("/api/v1/platform/plugins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pluginId\":\"customer-insights\",\"version\":\"9.0.0\",\"artifactDigest\":\"sha256:customer-insights-v1\"}")
                        .with(platform("platform.plugins.write")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("INCOMPATIBLE"));

        mockMvc.perform(post("/api/v1/platform/plugins/customer-insights:enable")
                        .with(platform("platform.plugins.write")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_PLUGIN_INCOMPATIBLE"));

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/plugins/customer-insights/summary")
                        .with(enterprise("enterprise.plugins.customer-insights.read")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void requiresRegistrationAndPermission() throws Exception {
        mockMvc.perform(post("/api/v1/platform/plugins/customer-insights:enable")
                        .with(platform("platform.plugins.write")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUMMER_PLUGIN_UNTRUSTED"));

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/plugins")
                        .with(enterprise("enterprise.plugins.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor platform(String capability) {
        return jwt().jwt(token -> token.subject("platform-admin").claim("platform_capabilities", List.of(capability)))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + capability));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor enterprise(String... capabilities) {
        return jwt().jwt(token -> token.subject("enterprise-admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of(capabilities))))
                .authorities(java.util.Arrays.stream(capabilities)
                        .map(value -> new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + value))
                        .toArray(org.springframework.security.core.GrantedAuthority[]::new));
    }
}
