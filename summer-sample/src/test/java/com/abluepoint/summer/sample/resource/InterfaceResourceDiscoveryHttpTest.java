package com.abluepoint.summer.sample.resource;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"management.endpoints.web.base-path=/ops", "management.endpoints.web.exposure.include=health,info"})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(InterfaceResourceDiscoveryHttpTest.Settings.class)
class InterfaceResourceDiscoveryHttpTest {
    @Autowired MockMvc mvc;

    @Test
    void discoversConfiguredProtocolActuatorAndNonMvcMappingsWithoutInventedDefaults() throws Exception {
        var response = mvc.perform(get("/api/v1/platform/interface-resources").with(jwt()
                        .jwt(token -> token.subject("catalog-reader").claim("platform_capabilities", List.of("platform.interface-resources.read")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_platform.interface-resources.read"))))
                .andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> items = JsonPath.read(response.getResponse().getContentAsString(), "$.data.items");
        assertThat(items).anySatisfy(item -> assertThat(item).containsEntry("normalizedPath", "/custom/token").containsEntry("httpMethod", "POST").containsEntry("resourceType", "PROTOCOL"));
        assertThat(items).anySatisfy(item -> assertThat(item).containsEntry("normalizedPath", "/ops/health").containsEntry("resourceType", "INFRASTRUCTURE"));
        assertThat(items).anySatisfy(item -> assertThat(item).containsEntry("normalizedPath", "/ws/v1/messages"));
        assertThat(items).noneSatisfy(item -> assertThat(item).containsEntry("normalizedPath", "/oauth2/token"));
        assertThat(items).noneSatisfy(item -> assertThat(item).containsEntry("normalizedPath", "/actuator/health"));
        assertThat(items).noneSatisfy(item -> assertThat(item).containsEntry("normalizedPath", "/ops/metrics"));
    }

    @Test
    void preservesDistinctRequestConditionsAndDoesNotTreatNewBusinessPackagesAsStatic() throws Exception {
        var response = mvc.perform(get("/api/v1/platform/interface-resources").with(jwt()
                        .jwt(token -> token.subject("catalog-reader").claim("platform_capabilities", List.of("platform.interface-resources.read")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_platform.interface-resources.read"))))
                .andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> items = JsonPath.read(response.getResponse().getContentAsString(), "$.data.items");
        var variants = items.stream().filter(row -> "/api/catalog-extension".equals(row.get("normalizedPath")) && "GET".equals(row.get("httpMethod"))).toList();
        assertThat(variants).hasSize(2);
        assertThat(items.stream().filter(row -> "/api/catalog-any".equals(row.get("normalizedPath"))).map(row -> row.get("httpMethod")).toList())
                .contains("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD").doesNotContain("ANY");
        assertThat(variants).allSatisfy(row -> assertThat(row).containsEntry("resourceType", "BUSINESS").containsEntry("permissionCode", "GET:/api/catalog-extension").containsKey("conditions"));
        assertThat(variants.get(0).get("conditions")).isNotEqualTo(variants.get(1).get("conditions"));
    }

    @Test
    void discoversInstalledMetadataFiltersAndFunctionalRoutes() throws Exception {
        var response = mvc.perform(get("/api/v1/platform/interface-resources").with(jwt()
                        .jwt(token -> token.subject("catalog-reader").claim("platform_capabilities", List.of("platform.interface-resources.read")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_platform.interface-resources.read"))))
                .andExpect(status().isOk()).andReturn();
        List<Map<String,Object>> items = JsonPath.read(response.getResponse().getContentAsString(), "$.data.items");
        assertThat(items.stream().map(row -> row.get("normalizedPath")).toList())
                .contains("/.well-known/openid-configuration", "/.well-known/oauth-authorization-server", "/api/catalog-functional/item");
        mvc.perform(get("/api/catalog-functional/item").with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void protocolAndInfrastructureMetadataAreConfigurableWithoutBypassingTheirOwnProtection() throws Exception {
        var response = mvc.perform(get("/api/v1/platform/interface-resources").with(jwt()
                .jwt(token -> token.subject("catalog-reader").claim("platform_capabilities", List.of("platform.interface-resources.read")))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.interface-resources.read"))))
                .andExpect(status().isOk()).andReturn();
        List<Map<String,Object>> items = JsonPath.read(response.getResponse().getContentAsString(), "$.data.items");
        var components = items.stream().filter(row -> List.of("PROTOCOL", "INFRASTRUCTURE").contains(row.get("resourceType"))).toList();
        assertThat(components).isNotEmpty();
        assertThat(components.stream().filter(row -> !Boolean.TRUE.equals(row.get("configurable")))
                .map(row -> row.get("contextId") + ":" + row.get("normalizedPath")).toList()).isEmpty();
        var websocket = items.stream().filter(row -> "/ws/v1/messages".equals(row.get("normalizedPath"))).findFirst().orElseThrow();
        assertThat(websocket).containsEntry("configurationPolicy", "COMPONENT_OWNED");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/login")
                .param("username", "not-a-user")).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/custom/token")
                .param("grant_type", "client_credentials")).andExpect(status().isUnauthorized());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Settings {
        @Bean org.springframework.web.servlet.function.RouterFunction<org.springframework.web.servlet.function.ServerResponse> catalogFunctionalRoute() {
            return org.springframework.web.servlet.function.RouterFunctions.route().path("/api/catalog-functional", builder ->
                    builder.GET("/item", request -> org.springframework.web.servlet.function.ServerResponse.ok().body("functional"))).build();
        }
        @Bean com.example.catalog.CatalogExtensionController catalogExtensionController() { return new com.example.catalog.CatalogExtensionController(); }
        @Bean AuthorizationServerSettings catalogTestAuthorizationSettings() {
            return AuthorizationServerSettings.builder().issuer("http://127.0.0.1:8080")
                    .tokenEndpoint("/custom/token").build();
        }
    }
}
