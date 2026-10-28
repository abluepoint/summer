package com.abluepoint.summer.sample.observability;

import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.authorization.AuthorizationService;
import com.abluepoint.summer.sample.authorization.EnterpriseRequestContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.config.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.SecurityFilterChain;

/** Apply the existing diagnostics capability to the raw monitoring endpoints. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "summer.security.resource-server", name = "enabled", havingValue = "true")
class MonitoringSecurityConfiguration {
    @Bean
    @Order(0)
    SecurityFilterChain monitoringSecurityFilterChain(HttpSecurity http,
            @Qualifier("summerResourceServerSecurity") Customizer<HttpSecurity> resourceServerSecurity,
            AuthorizationService authorization, RequestIdAccessor requestId)
            throws Exception {
        resourceServerSecurity.customize(http);
        http.securityMatcher("/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**",
                        "/internal/v1/diagnostics/runtime")
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
                }));
        return http.build();
    }
}
