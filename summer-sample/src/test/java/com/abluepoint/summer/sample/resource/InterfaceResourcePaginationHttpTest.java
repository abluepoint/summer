package com.abluepoint.summer.sample.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;

import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InterfaceResourcePaginationHttpTest {
    private static final String ROOT = "/api/v1/platform/interface-resources";
    private final InterfaceResourceHistory history = mock(InterfaceResourceHistory.class);
    private MockMvc mvc;
    private JwtAuthenticationToken reader;

    @BeforeEach
    void setup() {
        var items = IntStream.range(0, 25).mapToObj(i -> InterfaceResourceDescriptor.mvc(
                "GET", "/api/resource-" + i, "Fixture#read" + i, "fixture",
                ResourceType.BUSINESS, SensitiveLevel.NORMAL, true)).toList();
        var view = new InterfaceResourceCatalogView(items, 7);
        var service = new InterfaceResourceCatalogService(new StaticApplicationContext(), history);
        ReflectionTestUtils.setField(service, "current", view);
        when(history.protect(any())).thenAnswer(call -> call.getArgument(0));
        when(history.record(any())).thenReturn(view);
        when(history.read(6)).thenReturn(new InterfaceResourceCatalogView(items, 6));
        var requestId = mock(RequestIdAccessor.class);
        when(requestId.currentRequestId()).thenReturn("pagination-check");
        mvc = MockMvcBuilders.standaloneSetup(new InterfaceResourceCatalogController(service, requestId, history, mock(InterfaceServiceDirectory.class)))
                .setControllerAdvice(new com.abluepoint.summer.mvc.support.ApiExceptionHandler(
                        (code, arguments, locale) -> code.defaultMessage(),
                        new com.abluepoint.summer.mvc.error.DefaultErrorStatusResolver(), requestId))
                .build();
        reader = new JwtAuthenticationToken(Jwt.withTokenValue("fixture").header("alg", "none")
                .subject("catalog-reader").claim("platform_capabilities", List.of("platform.interface-resources.read"))
                .build(), List.of(new SimpleGrantedAuthority("SCOPE_platform.interface-resources.read")));
    }

    @Test
    void returnsOnlyTheRequestedPageWithoutRescanningOrReadingHistory() throws Exception {
        mvc.perform(get(ROOT).principal(reader).param("page", "0").param("size", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(10))
                .andExpect(jsonPath("$.data.total").value(25)).andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10)).andExpect(jsonPath("$.data.revision").value(7));
        mvc.perform(get(ROOT).principal(reader).param("page", "1").param("size", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(10))
                .andExpect(jsonPath("$.data.items[0].normalizedPath").value("/api/resource-10"));
        verifyNoInteractions(history);
    }

    @Test
    void handlesTheLastPageAndAnOverflowingOffset() throws Exception {
        mvc.perform(get(ROOT).principal(reader).param("page", "2").param("size", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(5));
        mvc.perform(get(ROOT).principal(reader).param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void rejectsUnboundedPagesAndRetainsAuthorizationChecks() throws Exception {
        for (String query : List.of("?page=-1&size=10", "?page=0&size=0", "?page=0&size=101"))
            mvc.perform(get(ROOT + query).principal(reader)).andExpect(status().isBadRequest());
        var denied = new JwtAuthenticationToken(Jwt.withTokenValue("fixture").header("alg", "none").subject("reader").build());
        mvc.perform(get(ROOT).principal(denied).param("page", "0").param("size", "10"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(history);
    }

    @Test
    void scopesEtagsToThePageAndPinsTheSnapshotRevision() throws Exception {
        String etag = mvc.perform(get(ROOT).principal(reader).param("page", "0").param("size", "10"))
                .andReturn().getResponse().getHeader("ETag");
        mvc.perform(get(ROOT).principal(reader).param("page", "0").param("size", "10").header("If-None-Match", etag))
                .andExpect(status().isNotModified());
        mvc.perform(get(ROOT).principal(reader).param("page", "1").param("size", "10").header("If-None-Match", etag))
                .andExpect(status().isOk());
        mvc.perform(get(ROOT).principal(reader).param("page", "1").param("size", "10").param("revision", "6"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revision").value(6))
                .andExpect(jsonPath("$.data.items.length()").value(10));
    }

    @Test
    void retainsTheLegacyCatalogAndAllowsAnExplicitFreshScan() throws Exception {
        mvc.perform(get(ROOT).principal(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(25)).andExpect(header().string("ETag", "\"7\""));
        mvc.perform(get(ROOT).principal(reader).param("page", "0").param("size", "10").param("refresh", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(10));
        verify(history, times(2)).record(any());
    }
}
