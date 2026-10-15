package com.abluepoint.summer.sample.authorization;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;
import com.abluepoint.summer.sample.resource.ResourcePermission;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthorizationRequestInterceptorTest {
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void missingMatchedMappingFailsClosedInsteadOfAuthorizingConcreteUri() throws Exception {
        var service = mock(AuthorizationService.class);
        var interceptor = new AuthorizationRequestInterceptor(service);
        authenticate();
        var request = new MockHttpServletRequest("GET", "/api/widgets/one");
        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), handler()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/widgets/one", "/api/widgets/menu-manifest"})
    void missingEnterpriseContextStillRequiresAnAuthorizationDecision(String uri) throws Exception {
        var service = mock(AuthorizationService.class);
        var interceptor = new AuthorizationRequestInterceptor(service);
        var jwt = Jwt.withTokenValue("test").header("alg", "none").subject("unassigned-reader").build();
        var authentication = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(service.decision(authentication, "ent-acme", "GET:/api/widgets/{widgetId}", null))
                .thenReturn(new AuthorizationService.Decision(false, "DEFAULT_DENY"));
        var request = new MockHttpServletRequest("GET", uri);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/widgets/{widgetId}");
        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), handler()))
                .isInstanceOf(AccessDeniedException.class);
        verify(service).decision(authentication, "ent-acme", "GET:/api/widgets/{widgetId}", null);
    }

    @Test
    void foreignEnterpriseDoesNotSelectTheTenantForGlobalBusinessOrPlatformResources() throws Exception {
        var service = mock(AuthorizationService.class);
        var interceptor = new AuthorizationRequestInterceptor(service);
        var jwt = Jwt.withTokenValue("test").header("alg", "none").subject("foreign-reader")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-other", "status", "ACTIVE"))).build();
        var authentication = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(service.decision(eq(authentication), eq("ent-acme"), anyString(), isNull()))
                .thenReturn(new AuthorizationService.Decision(false, "DEFAULT_DENY"));
        var request = new MockHttpServletRequest("GET", "/api/widgets/one");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/widgets/{widgetId}");
        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), handler()))
                .isInstanceOf(AccessDeniedException.class);
        var platformRequest = new MockHttpServletRequest("GET", "/api/v1/platform/interface-resources");
        var platformHandler = new HandlerMethod(new PlatformController(), PlatformController.class.getDeclaredMethod("read"));
        assertThatThrownBy(() -> interceptor.preHandle(platformRequest, new MockHttpServletResponse(), platformHandler))
                .isInstanceOf(AccessDeniedException.class);
        verify(service).decision(authentication, "ent-acme", "GET:/api/widgets/{widgetId}", null);
        verify(service).decision(authentication, "ent-acme", "platform.interface-resources.read", null);
    }

    @Test
    void parsedPatternUsesTheSameTemplateAsTheCatalog() throws Exception {
        var service = mock(AuthorizationService.class);
        var interceptor = new AuthorizationRequestInterceptor(service);
        var authentication = authenticate();
        when(service.decision(authentication, "ent-acme", "GET:/api/widgets/{widgetId}", null))
                .thenReturn(new AuthorizationService.Decision(true, "ROLE_ALLOW"));
        var request = new MockHttpServletRequest("GET", "/api/widgets/one");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                new PathPatternParser().parse("/api/widgets/{widgetId}"));
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handler())).isTrue();
        verify(service).decision(authentication, "ent-acme", "GET:/api/widgets/{widgetId}", null);
    }

    private JwtAuthenticationToken authenticate() {
        var jwt = Jwt.withTokenValue("test").header("alg", "none").subject("reader")
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))).build();
        var authentication = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    private HandlerMethod handler() throws Exception {
        return new HandlerMethod(new WidgetController(), WidgetController.class.getDeclaredMethod("read"));
    }

    static class WidgetController { public void read() {} }
    @ResourcePermission("platform.interface-resources.read")
    static class PlatformController { public void read() {} }
}
