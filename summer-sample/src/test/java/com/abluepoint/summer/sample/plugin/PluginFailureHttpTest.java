package com.abluepoint.summer.sample.plugin;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:plugin-failure-contract;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PluginFailureHttpTest {
    @Autowired MockMvc mvc;

    @Test
    void failuresKeepEveryBusinessMappingAndCannotMutateTheIncompatibleRegistration() throws Exception {
        var enterprise = jwt().jwt(token -> token.subject("plugin-reader")
                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.plugins.customer-insights.read", "customer.read"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.plugins.customer-insights.read"), new SimpleGrantedAuthority("SCOPE_customer.read"));
        failure(get("/api/v1/enterprises/ent-acme/plugins/customer-insights/summary"), enterprise,
                404, "SUMMER_NOT_FOUND", "Plugin is not registered");
        failure(register("{}"), platform(), 400, "SAMPLE_PLUGIN_INVALID", "pluginId, version and artifactDigest are required");
        failure(register("{\"pluginId\":\"customer-insights\",\"version\":\"1.0.0\",\"artifactDigest\":\"untrusted\"}"),
                platform(), 409, "SAMPLE_PLUGIN_UNTRUSTED", "Plugin artifact is not in the trusted catalog");
        failure(register("{}"), jwt().authorities(new SimpleGrantedAuthority("SCOPE_platform.plugins.write")),
                403, "SUMMER_FORBIDDEN", "Platform plugin capability denied");
        var created = mvc.perform(register("{\"pluginId\":\"customer-insights\",\"version\":\"9.0.0\",\"artifactDigest\":\"sha256:customer-insights-v1\"}").with(platform()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("INCOMPATIBLE"))
                .andReturn().getResponse().getContentAsString();
        Number revision = JsonPath.read(created, "$.data.revision");
        failure(post("/api/v1/platform/plugins/customer-insights:enable"), platform(), 409,
                "SAMPLE_PLUGIN_INCOMPATIBLE", "Plugin version 9.0.0 is incompatible with the supported contract 1.0.0");
        mvc.perform(get("/api/v1/platform/plugins").with(platform()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("INCOMPATIBLE"))
                .andExpect(jsonPath("$.data.items[0].version").value("9.0.0"))
                .andExpect(jsonPath("$.data.items[0].revision").value(revision.intValue()));
    }

    private MockHttpServletRequestBuilder register(String body) {
        return post("/api/v1/platform/plugins").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private ResultActions failure(MockHttpServletRequestBuilder request, RequestPostProcessor auth, int statusCode, String code, String message) throws Exception {
        return mvc.perform(request.with(auth).header("X-Request-Id", "plugin-failure-contract"))
                .andExpect(status().is(statusCode)).andExpect(header().string("X-Request-Id", "plugin-failure-contract"))
                .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message").value(message)).andExpect(jsonPath("$.error.violations").isEmpty())
                .andExpect(jsonPath("$.requestId").value("plugin-failure-contract"));
    }

    private RequestPostProcessor platform() {
        return jwt().jwt(token -> token.subject("plugin-admin").claim("platform_capabilities", List.of("platform.plugins.read", "platform.plugins.write")))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.plugins.read"), new SimpleGrantedAuthority("SCOPE_platform.plugins.write"));
    }
}
