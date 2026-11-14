package com.abluepoint.summer.sample.resource;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({InterfaceResourceUrlMappingHttpTest.Configuration.class, com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class})
class InterfaceResourceUrlMappingHttpTest {
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    @org.junit.jupiter.api.BeforeEach
    void grantExactCatalogRead() {
        current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), "catalog-reader",
                (handler, path, method) -> handler.getBeanType() == InterfaceResourceCatalogController.class
                        && method.equals("GET") && java.util.Set.of(
                                "/api/v1/platform/interface-resources", "/api/v1/platform/interface-resources/history",
                                "/api/v1/platform/interface-resources/history/{revision}",
                                "/api/v1/platform/interface-resources/diff").contains(path));
    }

    @Autowired MockMvc mvc;
    @Test void unadaptedBusinessHandlerCannotBypassManagedAuthorization() throws Exception {
        mvc.perform(get("/api/catalog-handler").with(jwt())).andExpect(status().isForbidden());
        var response = mvc.perform(get("/api/v1/platform/interface-resources").with(jwt()
                .jwt(token -> token.subject("catalog-reader").claim("scope", "platform.interface-resources.read").claim("platform_capabilities", List.of("platform.interface-resources.read")))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.interface-resources.read"))))
                .andExpect(status().isOk()).andReturn();
        List<Map<String,Object>> rows = JsonPath.read(response.getResponse().getContentAsString(), "$.data.items");
        var row = rows.stream().filter(item -> "/api/catalog-handler".equals(item.get("normalizedPath"))).findFirst().orElseThrow();
        assertThat(row).containsEntry("resourceType", "BUSINESS").containsEntry("configurable", false)
                .containsEntry("configurationPolicy", "MANAGED_ADAPTER_REQUIRED");
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean SimpleUrlHandlerMapping catalogUrlMapping() {
            HttpRequestHandler handler = (request, response) -> response.getWriter().write("unmanaged");
            return new SimpleUrlHandlerMapping(Map.of("/api/catalog-handler", handler), -2);
        }
    }
}
