package com.abluepoint.summer.sample.resource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({InterfaceResourceIncompleteHttpTest.OpaqueMapping.class, com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class})
class InterfaceResourceIncompleteHttpTest {
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
    @Test void unsupportedMappingsAreExplicitAndDoNotClaimCompleteDiscovery() throws Exception {
        mvc.perform(get("/api/v1/platform/interface-resources").with(jwt()
                        .jwt(token -> token.subject("catalog-reader").claim("scope", "platform.interface-resources.read").claim("platform_capabilities", List.of("platform.interface-resources.read")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_platform.interface-resources.read"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.complete").value(false))
                .andExpect(jsonPath("$.data.issues[0].code").value("SUMMER_RESOURCE_DISCOVERY_UNSUPPORTED"))
                .andExpect(header().exists("X-Request-Id"));
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class OpaqueMapping {
        @Bean org.springframework.web.servlet.HandlerMapping opaqueCustomHandlerMapping() { return request -> null; }
    }
}
