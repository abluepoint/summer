package com.abluepoint.summer.security.autoconfigure;

import org.junit.jupiter.api.Test;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Qualifier;
import com.abluepoint.summer.security.access.api.ResourceServerSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityApplicationOverrideTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SummerSecurityAutoConfiguration.class))
            .withUserConfiguration(ApplicationSecurityConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void applicationOwnsPrincipalAndClientPolicy() {
        contextRunner
                .withPropertyValues("summer.security.authorization-server.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(RegisteredClientRepository.class)).hasSize(1);
                    assertThat(context.getBeansOfType(UserDetailsService.class)).hasSize(1);
                    assertThat(context.getBean(RegisteredClientRepository.class))
                            .isSameAs(context.getBean("applicationRegisteredClientRepository"));
                    assertThat(context.getBean(UserDetailsService.class))
                            .isSameAs(context.getBean("applicationUserDetailsService"));
                });
    }

    @Test
    void fallbackAuditLoggerDoesNotCompeteWithTheDefaultApplicationLogger() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(com.abluepoint.summer.security.audit.spi.SecurityAuditLogger.class))
                    .isSameAs(context.getBean("summerSecurityAuditLogger"));
            assertThat(context.getBean("summerSecurityFallbackAuditLogger"))
                    .isInstanceOf(com.abluepoint.summer.security.audit.spi.SecurityAuditLogger.class);
        });
    }

    @Test
    void applicationCanReplacePrimaryAndNamedFallbackAuditSinks() {
        contextRunner.withUserConfiguration(ApplicationAuditConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("summerSecurityAuditLogger");
                    assertThat(context.getBean(com.abluepoint.summer.security.audit.spi.SecurityAuditLogger.class))
                            .isSameAs(context.getBean("applicationAuditLogger"));
                    assertThat(context.getBean("summerSecurityFallbackAuditLogger"))
                            .isSameAs(context.getBean(ApplicationAuditConfiguration.class).fallback);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationAuditConfiguration {
        final com.abluepoint.summer.security.audit.spi.SecurityAuditLogger fallback = (event, principal, attributes) -> {};

        @Bean
        com.abluepoint.summer.security.audit.spi.SecurityAuditLogger applicationAuditLogger() {
            return (event, principal, attributes) -> {};
        }

        @Bean(defaultCandidate = false)
        com.abluepoint.summer.security.audit.spi.SecurityAuditLogger summerSecurityFallbackAuditLogger() {
            return fallback;
        }
    }

    @Test
    void applicationResourceChainSharesAuthenticationWithoutCouplingBearerToCookies() {
        contextRunner.withUserConfiguration(ApplicationResourceSecurityConfiguration.class)
                .withPropertyValues("summer.security.authorization-server.enabled=false",
                        "summer.security.resource-server.enabled=true",
                        "summer.security.resource-server.protected-paths=/api/**")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SecurityContextRepository.class))
                            .isSameAs(context.getBean("applicationSessions"));
                    var security = context.getBean("springSecurityFilterChain", Filter.class);
                    for (String path : List.of("/api/probe", "/monitor")) {
                        var session = new MockHttpSession();
                        var cookieContext = SecurityContextHolder.createEmptyContext();
                        cookieContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                                "cookie-reader", null, List.of(new SimpleGrantedAuthority("diagnostics"))));
                        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                                cookieContext);
                        var cookie = resourceRequest(path, session);
                        var cookieResponse = new MockHttpServletResponse();
                        security.doFilter(cookie, cookieResponse, (request, response) -> {
                            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                                    .isEqualTo("cookie-reader");
                        });
                        assertThat(cookieResponse.getStatus()).isEqualTo(200);

                        var bearer = resourceRequest(path, session);
                        bearer.addHeader("Authorization", "Bearer valid-fixture");
                        var bearerResponse = new MockHttpServletResponse();
                        security.doFilter(bearer, bearerResponse, (request, response) -> {
                            assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                                    .isEqualTo("bearer-reader");
                            assertThat(((HttpServletRequest) request).getSession(false)).isNull();
                        });
                        assertThat(bearerResponse.getStatus()).isEqualTo(200);
                        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                                .isSameAs(cookieContext);

                        var invalid = resourceRequest(path, session);
                        invalid.addHeader("Authorization", "Bearer invalid-fixture");
                        var invalidResponse = new MockHttpServletResponse();
                        security.doFilter(invalid, invalidResponse, (request, response) -> {
                            throw new AssertionError("Invalid bearer must never fall back to the authorized cookie");
                        });
                        assertThat(invalidResponse.getStatus()).isEqualTo(401);
                        assertThat(invalidResponse.getContentAsString()).contains("SUMMER_UNAUTHORIZED");

                        session.invalidate();
                        var revokedResponse = new MockHttpServletResponse();
                        security.doFilter(resourceRequest(path, null), revokedResponse, (request, response) -> {
                            throw new AssertionError("A revoked session cannot authenticate a resource request");
                        });
                        assertThat(revokedResponse.getStatus()).isEqualTo(401);
                    }
                });
    }

    private static MockHttpServletRequest resourceRequest(
            String path, MockHttpSession session) {
        var request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        if (session != null) request.setSession(session);
        return request;
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationResourceSecurityConfiguration {
        @Bean
        SecurityContextRepository applicationSessions() {
            return new HttpSessionSecurityContextRepository();
        }

        @Bean
        JwtDecoder applicationDecoder() {
            return value -> {
                if (!"valid-fixture".equals(value)) throw new BadJwtException("Invalid fixture");
                return Jwt.withTokenValue(value).header("alg", "RS256")
                        .subject("bearer-reader").build();
            };
        }

        @Bean
        JwtAuthenticationConverter applicationJwtConverter() {
            var converter = new JwtAuthenticationConverter();
            converter.setJwtGrantedAuthoritiesConverter(jwt -> List.of(
                    new SimpleGrantedAuthority("diagnostics")));
            return converter;
        }

        @Bean
        @Order(0)
        SecurityFilterChain applicationMonitoringChain(
                HttpSecurity http,
                @Qualifier("summerResourceServerSecurity")
                ResourceServerSecurity security)
                throws Exception {
            security.customize(http);
            http.securityMatcher("/monitor").authorizeHttpRequests(requests -> requests.anyRequest().hasAuthority("diagnostics"));
            return http.build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class ApplicationSecurityConfiguration {

        @Bean
        RegisteredClientRepository applicationRegisteredClientRepository() {
            RegisteredClient client = RegisteredClient.withId("application-client")
                    .clientId("application-client")
                    .clientSecret("{noop}application-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .scope("customer.read")
                    .build();
            return new InMemoryRegisteredClientRepository(client);
        }

        @Bean
        UserDetailsService applicationUserDetailsService() {
            return new InMemoryUserDetailsManager(User.withUsername("application-user")
                    .password("{noop}application-secret")
                    .roles("USER")
                    .build());
        }
    }
}
