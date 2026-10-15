package com.abluepoint.summer.sample.observability;

import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.authorization.AuthorizationService;
import com.abluepoint.summer.sample.authorization.EnterpriseRequestContext;
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
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

/** Apply the existing diagnostics capability to the raw monitoring endpoints. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "summer.security.resource-server", name = "enabled", havingValue = "true")
class MonitoringSecurityConfiguration {
    @Bean
    @Order(0)
    SecurityFilterChain monitoringSecurityFilterChain(HttpSecurity http,
            JsonAuthenticationEntryPoint unauthenticated, JsonAccessDeniedHandler forbidden,
            ObjectProvider<SecurityContextRepository> sessions, ObjectProvider<JwtAuthenticationConverter> converters,
            AuthorizationService authorization, RequestIdAccessor requestId)
            throws Exception {
        var bearer = new org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationConverter();
        bearer.setAuthenticationDetailsSource(request ->
                new org.springframework.security.web.authentication.WebAuthenticationDetails(request.getRemoteAddr(), null));
        http.securityMatcher("/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**",
                        "/internal/v1/diagnostics/runtime")
                .addFilterBefore(new BearerSessionIsolationFilter(), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().access((authentication, context) -> {
                    var principal = authentication.get();
                    if (principal.getAuthorities().stream().noneMatch(a ->
                            "SCOPE_platform.capabilities.read".equals(a.getAuthority()))) return new AuthorizationDecision(false);
                    try {
                        return new AuthorizationDecision(authorization.decision(principal,
                                EnterpriseRequestContext.resolve(principal), "platform.capabilities.read",
                                requestId.currentRequestId()).allowed());
                    } catch (RuntimeException deniedOrUnavailable) {
                        return new AuthorizationDecision(false);
                    }
                }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context.securityContextRepository(new BearerOrSessionSecurityContextRepository(
                        sessions.getIfAvailable(DefaultSummerSessionStore::new))))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthenticated).accessDeniedHandler(forbidden))
                .oauth2ResourceServer(oauth -> oauth.authenticationConverter(bearer)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converters.getIfAvailable(JwtAuthenticationConverter::new)))
                        .authenticationEntryPoint(unauthenticated).accessDeniedHandler(forbidden));
        return http.build();
    }
}
