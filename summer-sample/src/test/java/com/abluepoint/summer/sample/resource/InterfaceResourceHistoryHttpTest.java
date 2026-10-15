package com.abluepoint.summer.sample.resource;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class InterfaceResourceHistoryHttpTest {
    @Autowired MockMvc mvc;
    @Autowired InterfaceResourceRevisionRepository revisions;
    @Autowired tools.jackson.databind.ObjectMapper mapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    private static final String ROOT = "/api/v1/platform/interface-resources";

    @Test
    void revisionsKeepAddedAndRemovedMappingsAndExposeImmutableDiffs() throws Exception {
        mvc.perform(get(ROOT + "/history").with(reader())).andExpect(status().isOk());
        long before = revision();
        var info = RequestMappingInfo.paths("/api/catalog-history-added").methods(RequestMethod.GET).build();
        mappings.registerMapping(info, new DynamicHandler(), DynamicHandler.class.getMethod("read"));
        long added;
        try {
            added = revision(); assertThat(added).isGreaterThan(before);
            var delta = mvc.perform(get(ROOT + "/diff").param("from", String.valueOf(before)).param("to", String.valueOf(added)).with(reader()))
                    .andExpect(status().isOk()).andReturn();
            List<Map<String,Object>> additions = JsonPath.read(delta.getResponse().getContentAsString(), "$.data.added");
            assertThat(additions.stream().map(row -> row.get("normalizedPath")).toList()).contains("/api/catalog-history-added");
        } finally { mappings.unregisterMapping(info); }
        long removed = revision();
        // Reusing a previous content snapshot is valid; the historical added revision stays immutable.
        assertThat(removed).isNotEqualTo(added);
        var snapshot = mvc.perform(get(ROOT + "/history/" + added).with(reader())).andExpect(status().isOk()).andReturn();
        List<Map<String,Object>> old = JsonPath.read(snapshot.getResponse().getContentAsString(), "$.data.items");
        assertThat(old.stream().map(row -> row.get("normalizedPath")).toList()).contains("/api/catalog-history-added");
        var delta = mvc.perform(get(ROOT + "/diff").param("from", String.valueOf(added)).param("to", String.valueOf(removed)).with(reader()))
                .andExpect(status().isOk()).andReturn();
        List<Map<String,Object>> removals = JsonPath.read(delta.getResponse().getContentAsString(), "$.data.removed");
        assertThat(removals.stream().map(row -> row.get("normalizedPath")).toList()).contains("/api/catalog-history-added");
    }

    @Test
    void replacementCannotInheritAnObservedAutomaticPermission() throws Exception {
        var info = RequestMappingInfo.paths("/api/catalog-reused").methods(RequestMethod.GET).build();
        mappings.registerMapping(info, new OriginalHandler(), OriginalHandler.class.getMethod("read"));
        long original;
        try { original = revision(); } finally { mappings.unregisterMapping(info); }
        mappings.registerMapping(info, new ReplacementHandler(), ReplacementHandler.class.getMethod("read"));
        try {
            // No catalog read first: the request boundary itself must reject a rebound route.
            mvc.perform(get("/api/catalog-reused").with(jwt())).andExpect(status().isForbidden());
            long replacement = revision();
            var response = mvc.perform(get(ROOT + "/diff").param("from", String.valueOf(original)).param("to", String.valueOf(replacement)).with(reader()))
                    .andExpect(status().isOk()).andReturn();
            List<Map<String,Object>> changed = JsonPath.read(response.getResponse().getContentAsString(), "$.data.changed");
            assertThat(changed.stream().map(row -> row.get("kind")).toList()).contains("REPLACED");
            mvc.perform(get("/api/catalog-reused").with(jwt())).andExpect(status().isForbidden());
        } finally { mappings.unregisterMapping(info); }
    }

    @Test void explicitOldAutomaticCodeCannotBypassReplacementProtection() throws Exception {
        var info = RequestMappingInfo.paths("/api/catalog-explicit-reused").methods(RequestMethod.GET).build();
        mappings.registerMapping(info, new OriginalHandler(), OriginalHandler.class.getMethod("read"));
        try { revision(); } finally { mappings.unregisterMapping(info); }
        mappings.registerMapping(info, new ExplicitOldHandler(), ExplicitOldHandler.class.getMethod("read"));
        try {
            mvc.perform(get("/api/catalog-explicit-reused").with(jwt())).andExpect(status().isForbidden());
            var restored = new InterfaceResourceHistory(revisions, mapper);
            restored.protect(new InterfaceResourceCatalog(List.of()));
            assertThat(restored.isReplaced("GET:/api/catalog-explicit-reused", ExplicitOldHandler.class.getName() + "#read[]")).isTrue();
        } finally { mappings.unregisterMapping(info); }
    }

    @Test void historyRejectsInvalidMissingAndUnauthorizedRequestsWithCorrelationIds() throws Exception {
        mvc.perform(get(ROOT + "/history").param("size", "0").with(reader())).andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.requestId").isNotEmpty());
        mvc.perform(get(ROOT + "/history/9223372036854775807").with(reader())).andExpect(status().isNotFound());
        mvc.perform(get(ROOT + "/diff").param("from", "0").param("to", "1").with(reader())).andExpect(status().isBadRequest());
        mvc.perform(get(ROOT + "/history").with(jwt())).andExpect(status().isForbidden());
    }

    public static class ExplicitOldHandler {
        @ResourcePermission("GET:/api/catalog-explicit-reused")
        @org.springframework.web.bind.annotation.ResponseBody public String read() { return "replacement"; }
    }

    public static class OriginalHandler {
        @org.springframework.web.bind.annotation.ResponseBody public String read() { return "original"; }
    }
    public static class ReplacementHandler {
        @org.springframework.web.bind.annotation.ResponseBody public String read() { return "replacement"; }
    }

    private long revision() throws Exception {
        var response = mvc.perform(get(ROOT).with(reader())).andExpect(status().isOk()).andReturn();
        return ((Number) JsonPath.read(response.getResponse().getContentAsString(), "$.data.revision")).longValue();
    }
    private static RequestPostProcessor reader() {
        return jwt().jwt(token -> token.subject("catalog-reader").claim("platform_capabilities", List.of("platform.interface-resources.read")))
                .authorities(new SimpleGrantedAuthority("SCOPE_platform.interface-resources.read"));
    }
    public static class DynamicHandler { public String read() { return "metadata fixture"; } }
}
