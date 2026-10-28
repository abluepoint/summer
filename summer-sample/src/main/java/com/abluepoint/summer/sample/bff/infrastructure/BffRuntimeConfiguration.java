package com.abluepoint.summer.sample.bff.infrastructure;

import com.abluepoint.summer.sample.bff.api.BffCookiePolicy;
import com.abluepoint.summer.sample.bff.application.BffSessionService;
import com.abluepoint.summer.sample.bff.application.SummerMcpClient;
import com.abluepoint.summer.sample.bff.domain.OidcCodeExchanger;

import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/** Runtime wiring for the optional same-origin BFF profile. */
@Configuration
@Profile("bff-runtime")
public class BffRuntimeConfiguration {

    /**
     * The BFF authenticates state-changing requests with its Product Session and
     * explicit CSRF/Origin filter. It must not be rejected by the resource-server
     * chain, which only protects Summer's /api/** resource endpoints.
     */
    @Bean
    @Order(0)
    SecurityFilterChain bffSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/bff/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    InMemoryBffStateStore bffStateStore() {
        return new InMemoryBffStateStore();
    }

    @Bean
    InMemoryProductSessionStore productSessionStore() {
        return new InMemoryProductSessionStore();
    }

    @Bean
    BffTokenGenerator bffTokenGenerator() {
        return new BffTokenGenerator();
    }

    @Bean
    OidcCodeExchanger oidcCodeExchanger() {
        return new UnavailableOidcCodeExchanger();
    }

    @Bean
    BffSessionService bffSessionService(
            InMemoryBffStateStore stateStore,
            InMemoryProductSessionStore sessionStore,
            OidcCodeExchanger exchanger,
            BffTokenGenerator tokenGenerator) {
        return new BffSessionService(stateStore, sessionStore, exchanger, tokenGenerator);
    }

    @Bean
    BffCookiePolicy bffCookiePolicy(
            @Value("${summer.sample.bff.authorize-base-url:http://127.0.0.1:8080/}") String authorizeBaseUrl,
            @Value("${summer.sample.bff.cookie-path:/}") String cookiePath) {
        return new BffCookiePolicy(authorizeBaseUrl, cookiePath);
    }

    @Bean
    SummerMcpClient summerMcpClient() {
        return new UnavailableSummerMcpClient();
    }

    @Bean
    FilterRegistrationBean<Filter> bffCsrfFilter(
            @Value("${summer.sample.bff.allowed-origins:http://127.0.0.1:3000}") String allowedOrigins) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new BffCsrfFilter(List.of(allowedOrigins.split(","))));
        registration.addUrlPatterns("/bff/*");
        registration.setOrder(-100);
        return registration;
    }
}
