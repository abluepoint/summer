package com.abluepoint.summer.sample.resource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="summer.sample.parameter-events.scheduling-enabled=false")
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class InterfaceResourceFailureHttpTest {
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    @org.junit.jupiter.api.BeforeEach
    void grantExactCatalogRead() {
        current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), "catalog-contract",
                (handler, path, method) -> handler.getBeanType() == InterfaceResourceCatalogController.class
                        && method.equals("GET") && java.util.Set.of(
                                "/api/v1/platform/interface-resources", "/api/v1/platform/interface-resources/history",
                                "/api/v1/platform/interface-resources/history/{revision}",
                                "/api/v1/platform/interface-resources/diff").contains(path));
    }

    private static final String ROOT = "/api/v1/platform/interface-resources";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void missingHistoryAndEitherDiffRevisionShareTheOriginalNotFoundContract() throws Exception {
        var catalog = mvc.perform(get(ROOT).with(reader())).andExpect(status().isOk()).andReturn();
        String revision = json.readTree(catalog.getResponse().getContentAsString()).path("data").path("revision").asString();
        for (var request : List.of(get(ROOT + "/history/9223372036854775807"),
                get(ROOT + "/diff").param("from", "9223372036854775807").param("to", revision),
                get(ROOT + "/diff").param("from", revision).param("to", "9223372036854775807"))) {
            failure(request, 404, "SUMMER_RESOURCE_REVISION_NOT_FOUND", "Interface resource revision was not found");
        }
    }

    @Test
    void invalidHistoryArgumentsKeepTheirLiteralReasons() throws Exception {
        failure(get(ROOT + "/history").param("size", "0"), 400, "SUMMER_RESOURCE_HISTORY_INVALID",
                "page must be non-negative and size must be between 1 and 100");
        failure(get(ROOT + "/history/0"), 400, "SUMMER_RESOURCE_HISTORY_INVALID", "revision must be positive");
        failure(get(ROOT + "/diff").param("from", "0").param("to", "1"),
                400, "SUMMER_RESOURCE_HISTORY_INVALID", "revision must be positive");
    }

    @Test
    void deniedCatalogHistoryAndDiffKeepCorrelationAndForbiddenCode() throws Exception {
        for (var request : List.of(get(ROOT), get(ROOT + "/history"), get(ROOT + "/history/1"),
                get(ROOT + "/diff").param("from", "1").param("to", "1"))) {
            mvc.perform(request.with(jwt()).header("X-Request-Id", "resource-denied"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                    .andExpect(jsonPath("$.requestId").value("resource-denied"))
                    .andExpect(header().string("X-Request-Id", "resource-denied"));
        }
    }

    private void failure(MockHttpServletRequestBuilder request, int status, String code, String message) throws Exception {
        mvc.perform(request.with(reader()).header("X-Request-Id", "resource-contract"))
                .andExpect(status().is(status)).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value(code)).andExpect(jsonPath("$.error.message").value(message))
                .andExpect(jsonPath("$.error.violations").isEmpty()).andExpect(jsonPath("$.error.cause").doesNotExist())
                .andExpect(jsonPath("$.requestId").value("resource-contract"))
                .andExpect(header().string("X-Request-Id", "resource-contract"));
    }
    private static RequestPostProcessor reader() {
        return jwt().jwt(token -> token.subject("catalog-contract").claim("scope", "platform.interface-resources.read").claim("platform_capabilities", List.of("platform.interface-resources.read")))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.interface-resources.read"));
    }
}
