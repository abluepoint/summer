package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.authorization.api.security.ManagedRequestAuthorization;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthorizationRequestInterceptorTest {
  @AfterEach void clearContext() {
    SecurityContextHolder.clearContext();
    org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
  }
  private JwtAuthenticationToken authenticate(String tenant) {
    var auth=new JwtAuthenticationToken(Jwt.withTokenValue("test").header("alg","none").subject("reader")
        .claim("enterprise_memberships",tenant==null?List.of():List.of(Map.of("enterpriseId",tenant,"status","ACTIVE"))).build());
    SecurityContextHolder.getContext().setAuthentication(auth); return auth;
  }
  private AuthorizationRequestInterceptor interceptor(AuthorizationService legacy,ManagedRequestAuthorization current) {
    var result=new AuthorizationRequestInterceptor(legacy,()->null,mock(UnifiedAuthorizationService.class),mock(LegacyResourceAdapter.class));
    org.springframework.test.util.ReflectionTestUtils.setField(result,"managedRequests",current); return result;
  }
  private HandlerMethod handler() throws Exception { return new HandlerMethod(new WidgetController(),WidgetController.class.getDeclaredMethod("read")); }

  @Test void unqualifiedConfigurationRouteUsesTheActiveEnterpriseInsteadOfSampleTenant() throws Exception {
    authenticate("ent-north");
    var request=new MockHttpServletRequest("GET","/api/system-parameters");
    Class<?> owner=Class.forName("com.abluepoint.summer.sample.systemparameter.SystemParameterController");
    var method=Arrays.stream(owner.getDeclaredMethods()).filter(m->m.getName().equals("list")).findFirst().orElseThrow();
    request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,new HandlerMethod(mock(owner),method));
    org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
    assertThat(ManagedRequestAuthorization.domain(request)).isEqualTo(Domain.enterprise("ent-north"));
  }
  @Test void missingMatchedMappingFailsClosedInsteadOfAuthorizingConcreteUri() throws Exception {
    var legacy=mock(AuthorizationService.class); authenticate("ent-acme");
    var request=new MockHttpServletRequest("GET","/api/widgets/one");
    assertThatThrownBy(()->interceptor(legacy,null).preHandle(request,new MockHttpServletResponse(),handler()))
        .isInstanceOf(AccessDeniedException.class).hasMessage("Matched resource mapping is unavailable");
    verifyNoInteractions(legacy);
  }
  @ParameterizedTest @ValueSource(strings={"/api/widgets/one","/api/widgets/menu-manifest"})
  void missingEnterpriseContextNeverFallsBackToTheSampleTenant(String uri) throws Exception {
    var legacy=mock(AuthorizationService.class); authenticate(null);
    var request=new MockHttpServletRequest("GET",uri); request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/api/widgets/{widgetId}");
    assertThatThrownBy(()->interceptor(legacy,null).preHandle(request,new MockHttpServletResponse(),handler()))
        .isInstanceOf(AccessDeniedException.class).hasMessage("Current managed resource adapter required");
    verifyNoInteractions(legacy);
  }
  @Test void foreignEnterpriseDoesNotSelectTheTenantForGlobalBusinessOrPlatformResources() throws Exception {
    authenticate("ent-other"); var request=new MockHttpServletRequest("GET","/api/widgets/one");
    request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,handler());
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/api/widgets/{widgetId}");
    assertThat(ManagedRequestAuthorization.domain(request)).isNull();
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/api/v1/platform/interface-resources");
    assertThat(ManagedRequestAuthorization.domain(request)).isEqualTo(Domain.platform());
  }
  @Test void parsedPatternIsPassedUnchangedToTheCurrentFinalHandlerGuard() throws Exception {
    var legacy=mock(AuthorizationService.class); var current=mock(ManagedRequestAuthorization.class); var auth=authenticate("ent-acme");
    var request=new MockHttpServletRequest("GET","/api/widgets/one"); var handler=handler();
    var pattern=new PathPatternParser().parse("/api/widgets/{widgetId}"); request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,pattern);
    when(current.finalHandler(request,handler,auth)).thenReturn(true);
    assertThat(interceptor(legacy,current).preHandle(request,new MockHttpServletResponse(),handler)).isTrue();
    assertThat(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)).isSameAs(pattern);
    verify(current).finalHandler(request,handler,auth); verifyNoInteractions(legacy);
  }
  static class WidgetController { public void read() {} }
}
