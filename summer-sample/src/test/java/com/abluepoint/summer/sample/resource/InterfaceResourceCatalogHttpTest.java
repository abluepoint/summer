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
class InterfaceResourceCatalogHttpTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void returnsAProtectedCompleteCatalogWithBusinessProtocolAndInfrastructureResources() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/platform/interface-resources")
                        .with(jwt().jwt(token -> token.subject("platform-admin")
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
                .andExpect(jsonPath("$.data.items[0].permissionCode").isString())
                .andExpect(jsonPath("$.data.items[0].configurable").isBoolean())
                .andReturn();

        List<Map<String, Object>> items = JsonPath.read(result.getResponse().getContentAsString(), "$.data.items");
        assertThat(items).anySatisfy(item -> assertThat(item)
                .containsEntry("normalizedPath", "/api/customers")
                .containsEntry("httpMethod", "GET")
                .containsEntry("resourceType", "BUSINESS")
                .containsEntry("permissionCode", "GET:/api/customers"));
        assertThat(items).anySatisfy(item -> assertThat(item)
                .containsEntry("normalizedPath", "/oauth2/token")
                .containsEntry("resourceType", "PROTOCOL")
                .containsEntry("configurable", true));
        assertThat(items).anySatisfy(item -> assertThat(item)
                .containsEntry("normalizedPath", "/actuator/health")
                .containsEntry("resourceType", "INFRASTRUCTURE"));
        assertThat(items).anySatisfy(item -> assertThat(item)
                .containsEntry("normalizedPath", "/api/v1/platform/interface-resources")
                .containsEntry("permissionCode", "platform.interface-resources.read"));
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
    void explicitPermissionAnnotationOverridesTheGeneratedCode() {
        InterfaceResourceCatalog catalog = new InterfaceResourceCatalog(List.of(
                InterfaceResourceDescriptor.mvc(
                        "POST", "/api/overridden", "ExampleController#post", "example",
                        ResourceType.BUSINESS, SensitiveLevel.SENSITIVE, "example.special.write", false)
        ));

        assertThat(catalog.items()).singleElement()
                .extracting(InterfaceResourceDescriptor::permissionCode)
                .isEqualTo("example.special.write");
    }

    @Test
    void rejectsConflictingMethodsAndPaths() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new InterfaceResourceCatalog(List.of(
                InterfaceResourceDescriptor.mvc(
                        "GET", "/api/conflict", "One#read", "one",
                        ResourceType.BUSINESS, SensitiveLevel.NORMAL, null, false),
                InterfaceResourceDescriptor.mvc(
                        "GET", "/api/conflict", "Two#read", "two",
                        ResourceType.BUSINESS, SensitiveLevel.NORMAL, null, false)
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Conflicting interface resource");
    }
}
