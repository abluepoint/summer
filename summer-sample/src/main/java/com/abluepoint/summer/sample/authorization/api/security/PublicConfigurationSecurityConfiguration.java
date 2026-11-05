package com.abluepoint.summer.sample.authorization.api.security;

import com.abluepoint.summer.security.autoconfigure.storage.BearerOrSessionSecurityContextRepository;
import com.abluepoint.summer.security.autoconfigure.storage.BearerSessionIsolationFilter;
import com.abluepoint.summer.security.autoconfigure.storage.DefaultSummerSessionStore;
import com.abluepoint.summer.security.web.JsonAccessDeniedHandler;
import com.abluepoint.summer.security.web.JsonAuthenticationEntryPoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

/** Validate supplied credentials; the actual MVC guard still decides PUBLIC/LOGIN/ROLE access. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "summer.security.resource-server",
    name = "enabled",
    havingValue = "true")
class PublicConfigurationSecurityConfiguration {
  @Bean
  // Precedes the generic resource chain (2), including configured /public/** overlaps.
  // These six routes are disjoint from the Authorization Server's protocol matcher (1).
  @Order(1)
  SecurityFilterChain publicConfigurationSecurityFilterChain(
      HttpSecurity http,
      JsonAuthenticationEntryPoint entryPoint,
      JsonAccessDeniedHandler deniedHandler,
      ObjectProvider<SecurityContextRepository> sessionStores,
      ObjectProvider<JwtAuthenticationConverter> converters)
      throws Exception {
    var bearer = new BearerTokenAuthenticationConverter();
    bearer.setAuthenticationDetailsSource(
        request -> new WebAuthenticationDetails(request.getRemoteAddr(), null));
    http.securityMatcher(
            "/public/dictionaries/{code}",
            "/public/dictionaries/{code}/items/{value}",
            "/public/enterprises/{enterpriseId}/dictionaries/{code}",
            "/public/enterprises/{enterpriseId}/dictionaries/{code}/items/{value}",
            "/public/system-parameters/{key}",
            "/public/enterprises/{enterpriseId}/system-parameters/{key}")
        .addFilterBefore(new BearerSessionIsolationFilter(), SecurityContextHolderFilter.class)
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .securityContext(
            context ->
                context.securityContextRepository(
                    new BearerOrSessionSecurityContextRepository(
                        sessionStores.getIfAvailable(DefaultSummerSessionStore::new))))
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        .csrf(
            csrf ->
                csrf.requireCsrfProtectionMatcher(
                    request ->
                        CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)
                            && request.getRequestedSessionId() != null
                            && !BearerOrSessionSecurityContextRepository.hasBearerCredentials(
                                request)))
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .authenticationConverter(bearer)
                    .jwt(
                        jwt ->
                            jwt.jwtAuthenticationConverter(
                                converters.getIfAvailable(JwtAuthenticationConverter::new)))
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(deniedHandler));
    return http.build();
  }
}
