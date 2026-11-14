package com.abluepoint.summer.sample.resource;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class InterfaceResourceCatalogHttpTest {
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    @org.junit.jupiter.api.BeforeEach
    void grantExactCatalogRead() {
        current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), "platform-admin",
                (handler, path, method) -> handler.getBeanType() == InterfaceResourceCatalogController.class
                        && method.equals("GET") && java.util.Set.of(
                                "/api/v1/platform/interface-resources", "/api/v1/platform/interface-resources/history",
                                "/api/v1/platform/interface-resources/history/{revision}",
                                "/api/v1/platform/interface-resources/diff").contains(path));
    }


    @Autowired
    MockMvc mockMvc;

    @Test
    void returnsAProtectedCompleteCatalogWithBusinessProtocolAndInfrastructureResources() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/platform/interface-resources")
                        .with(jwt().jwt(token -> token.subject("platform-admin").claim("scope", "platform.interface-resources.read")
                                .claim("platform_capabilities", List.of("platform.interface-resources.read")))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "SCOPE_platform.interface-resources.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].httpMethod").isString())
                .andExpect(jsonPath("$.data.items[0].normalizedPath").isString())
                .andExpect(jsonPath("$.data.items[0].handler").isString())
                .andExpect(jsonPath("$.data.items[0].module").isString())
                .andExpect(jsonPath("$.data.items[0].resourceType").isString())
                .andExpect(jsonPath("$.data.items[0].sensitiveLevel").isString())
                .andExpect(jsonPath("$.data.items[0].permissionCode").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].configurable").isBoolean())
                .andReturn();

        List<Map<String, Object>> items = JsonPath.read(result.getResponse().getContentAsString(), "$.data.items");
        assertThat(items).anySatisfy(item -> assertThat(item)
                .containsEntry("normalizedPath", "/api/customers")
                .containsEntry("httpMethod", "GET")
                .containsEntry("resourceType", "BUSINESS")
                .containsEntry("configurable", true)
                .doesNotContainKey("permissionCode"));
        assertThat(items).filteredOn(item -> "BUSINESS".equals(item.get("resourceType")))
                .isNotEmpty().allSatisfy(item -> assertThat(item).containsEntry("configurable", true));
        assertThat(items).anySatisfy(item -> assertThat(item)
                .containsEntry("normalizedPath", "/oauth2/token")
                .containsEntry("resourceType", "PROTOCOL")
                .containsEntry("configurable", true));
        assertThat(items).anySatisfy(item -> assertThat(item)
                .containsEntry("normalizedPath", "/actuator/health")
                .containsEntry("resourceType", "INFRASTRUCTURE"));
        assertThat(items).anySatisfy(item -> assertThat(item)
                .containsEntry("normalizedPath", "/api/v1/platform/interface-resources")
                .doesNotContainKey("permissionCode"));
    }

    @Test
    void rejectsUnauthenticatedAndUnauthorizedCatalogReads() throws Exception {
        mockMvc.perform(get("/api/v1/platform/interface-resources"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SUMMER_UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/platform/interface-resources")
                        .with(jwt().jwt(token -> token.subject("ordinary-user"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void currentDescriptorsContainMappingIdentityWithoutBusinessPermissionCodes() {
        InterfaceResourceCatalog catalog = new InterfaceResourceCatalog(List.of(
                InterfaceResourceDescriptor.mvc(
                        "POST", "/api/overridden", "ExampleController#post", "example",
                        ResourceType.BUSINESS, SensitiveLevel.SENSITIVE, false)
        ));

        assertThat(catalog.items()).singleElement()
                .extracting(InterfaceResourceDescriptor::normalizedPath)
                .isEqualTo("/api/overridden");
        assertThat(new tools.jackson.databind.ObjectMapper().writeValueAsString(catalog.items()))
                .doesNotContain("permissionCode");
    }

    @Test
    void rejectsConflictingMethodsAndPaths() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new InterfaceResourceCatalog(List.of(
                InterfaceResourceDescriptor.mvc(
                        "GET", "/api/conflict", "One#read", "one",
                        ResourceType.BUSINESS, SensitiveLevel.NORMAL, false),
                InterfaceResourceDescriptor.mvc(
                        "GET", "/api/conflict", "Two#read", "two",
                        ResourceType.BUSINESS, SensitiveLevel.NORMAL, false)
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Conflicting interface resource");
    }
}
