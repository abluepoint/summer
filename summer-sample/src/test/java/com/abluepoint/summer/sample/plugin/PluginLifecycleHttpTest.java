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

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:plugin-lifecycle;MODE=MySQL;DB_CLOSE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class PluginLifecycleHttpTest {
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    @Autowired org.springframework.jdbc.core.JdbcTemplate pluginDb;
    String pluginEnterpriseRole;
    @org.junit.jupiter.api.BeforeEach
    void grantExactPluginOperations() {
        for (String subject : java.util.List.of("platform-admin"))
            current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), subject,
                    (handler, path, method) -> handler.getBeanType() == PluginController.class
                            && path.startsWith("/api/v1/platform/plugins"));
        for (String subject : java.util.List.of("enterprise-admin")) {
            pluginEnterpriseRole = current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"), subject,
                    (handler, path, method) -> handler.getBeanType() == PluginController.class
                            && path.startsWith("/api/v1/enterprises/{enterpriseId}/plugins"));
            current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.enterprise("ent-acme"), subject,
                    (handler, path, method) -> handler.getBeanType().getName().equals("com.abluepoint.summer.sample.systemparameter.PublicSystemParameterController")
                            && method.equals("GET") && path.equals("/public/enterprises/{enterpriseId}/system-parameters/{key}"));
            current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), subject,
                    (handler, path, method) -> handler.getBeanType().getName().equals("com.abluepoint.summer.sample.customer.CustomerController")
                            && method.equals("GET") && path.equals("/api/customers"));
        }
        grantTheActualPluginMenu();
    }

    @Autowired MockMvc mockMvc;

    private void grantTheActualPluginMenu() {
        String permission = "plugin-menu-" + java.util.UUID.randomUUID();
        String domain = "ENTERPRISE:ent-acme";
        pluginDb.update("insert into summer_menu_manifest_item(menu_id,path,title_key,icon_key,sort_order,manifest_version,created_at,updated_at) values ('customer-insights','/plugins/customer-insights','Insights','',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        pluginDb.update("update summer_menu_publication set published=true,manifest_version=1 where publication_id='global'");
        pluginDb.update("insert into summer_permission_identity(domain_key,permission_id,code,type) values (?,?,?,'MENU')", domain,permission,permission);
        pluginDb.update("insert into summer_permission(domain_key,permission_id,code,name,type,access_mode,group_id,status,version) values (?,?,?,'Plugin menu','MENU','AUTHORIZED',?,'ACTIVE',1)", domain,permission,permission,pluginEnterpriseRole);
        pluginDb.update("insert into summer_permission_resource(domain_key,permission_id,resource_type,resource_id) values (?,?,'MENU','customer-insights')",domain,permission);
        pluginDb.update("insert into summer_role_permission(domain_key,role_id,permission_id) values (?,?,?)",domain,pluginEnterpriseRole,permission);
        pluginDb.update("update summer_authorization_domain set revision=revision+1 where domain_key=?",domain);
    }

    @Test
    void invalidRegistrationUsesTheSampleBusinessNamespace() throws Exception {
        mockMvc.perform(post("/api/v1/platform/plugins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pluginId\":\"INVALID\",\"version\":\"\",\"artifactDigest\":\"sha256:customer-insights-v1\"}")
                        .with(platform("platform.plugins.write")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SAMPLE_PLUGIN_INVALID"));
    }

    @Test
    void incompatibleEnableUsesTheSampleBusinessNamespace() throws Exception {
        mockMvc.perform(post("/api/v1/platform/plugins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pluginId\":\"customer-insights\",\"version\":\"9.0.0\",\"artifactDigest\":\"sha256:customer-insights-v1\"}")
                        .with(platform("platform.plugins.write")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/platform/plugins/customer-insights:enable")
                        .with(platform("platform.plugins.write")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SAMPLE_PLUGIN_INCOMPATIBLE"));
    }

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
                        .with(enterprise("enterprise.plugins.read", "enterprise.plugins.customer-insights.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].pluginId").value("customer-insights"))
                .andExpect(jsonPath("$.data.items[0].menus[0].menuId").value("customer-insights"));

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/plugins/customer-insights/summary")
                        .with(enterpriseWithScopes("enterprise.plugins.customer-insights.read customer.read system.parameter.read platform.dictionary.read",
                                "enterprise.plugins.customer-insights.read", "customer.read")))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                        .as("Plugin current positive: resolved=%s; body=%s", result.getResolvedException(),
                                result.getResponse().getContentAsString()).isEqualTo(200))
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
                .andExpect(jsonPath("$.error.code").value("SAMPLE_PLUGIN_UNTRUSTED"));

        mockMvc.perform(post("/api/v1/platform/plugins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pluginId\":\"customer-insights\",\"version\":\"9.0.0\",\"artifactDigest\":\"sha256:customer-insights-v1\"}")
                        .with(platform("platform.plugins.write")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("INCOMPATIBLE"));

        mockMvc.perform(post("/api/v1/platform/plugins/customer-insights:enable")
                        .with(platform("platform.plugins.write")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SAMPLE_PLUGIN_INCOMPATIBLE"));

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
                .andExpect(jsonPath("$.error.code").value("SAMPLE_PLUGIN_UNTRUSTED"));

        mockMvc.perform(get("/api/v1/enterprises/ent-acme/plugins")
                        .with(enterprise("enterprise.plugins.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor platform(String capability) {
        return jwt().jwt(token -> token.claim("scope", capability).subject("platform-admin").claim("platform_capabilities", List.of(capability)))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + capability));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor enterprise(String... capabilities) {
        return enterpriseWithScopes(String.join(" ", capabilities), capabilities);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor enterpriseWithScopes(String scopes, String... capabilities) {
        return jwt().jwt(token -> token.claim("scope", scopes).subject("enterprise-admin")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of(capabilities))))
                .authorities(java.util.Arrays.stream(capabilities)
                        .map(value -> new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + value))
                        .toArray(org.springframework.security.core.GrantedAuthority[]::new));
    }
}
