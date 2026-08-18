package com.abluepoint.summer.security.autoconfigure;

import com.abluepoint.summer.security.audit.SecurityAuditLogger;
import com.abluepoint.summer.security.audit.Slf4jSecurityAuditLogger;
import com.abluepoint.summer.security.web.JsonAccessDeniedHandler;
import com.abluepoint.summer.security.web.JsonAuthenticationEntryPoint;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@AutoConfiguration
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({SecurityFilterChain.class, OAuth2AuthorizationServerConfigurer.class})
@EnableConfigurationProperties(SummerSecurityProperties.class)
@ConditionalOnProperty(prefix = "summer.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SummerSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SecurityAuditLogger summerSecurityAuditLogger() {
        return new Slf4jSecurityAuditLogger();
    }

    @Bean
    @ConditionalOnMissingBean
    SecurityAuthenticationAuditListener summerSecurityAuthenticationAuditListener(SecurityAuditLogger auditLogger) {
        return new SecurityAuthenticationAuditListener(auditLogger);
    }

    @Bean
    @ConditionalOnMissingBean
    JsonAuthenticationEntryPoint summerJsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new JsonAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    JsonAccessDeniedHandler summerJsonAccessDeniedHandler(ObjectMapper objectMapper) {
        return new JsonAccessDeniedHandler(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    MethodSecurityExceptionHandler summerMethodSecurityExceptionHandler() {
        return new MethodSecurityExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    PasswordEncoder summerPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    RegisteredClientRepository summerRegisteredClientRepository(
            SummerSecurityProperties properties,
            PasswordEncoder passwordEncoder
    ) {
        List<SummerSecurityProperties.Client> configured = properties.getAuthorizationServer().getClients();
        if (configured.isEmpty()) {
            throw new IllegalStateException("At least one summer.security.authorization-server.clients entry is required");
        }

        List<RegisteredClient> clients = configured.stream()
                .map(client -> registeredClient(client, passwordEncoder))
                .toList();
        return new InMemoryRegisteredClientRepository(clients);
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    UserDetailsService summerUserDetailsService(SummerSecurityProperties properties, PasswordEncoder passwordEncoder) {
        SummerSecurityProperties.User configured = properties.getAuthorizationServer().getUser();
        if (configured.getUsername() == null || configured.getPassword() == null) {
            return new InMemoryUserDetailsManager();
        }
        return new InMemoryUserDetailsManager(User.withUsername(configured.getUsername())
                .password(passwordEncoder.encode(configured.getPassword()))
                .roles("USER")
                .build());
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    JWKSource<SecurityContext> summerJwkSource() {
        try {
            RSAKey rsaKey = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
            JWKSet jwkSet = new JWKSet(rsaKey);
            return (selector, context) -> selector.select(jwkSet);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate authorization server signing key", exception);
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    JwtDecoder summerJwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    AuthorizationServerSettings summerAuthorizationServerSettings(SummerSecurityProperties properties) {
        return AuthorizationServerSettings.builder()
                .issuer(properties.getAuthorizationServer().getIssuer())
                .build();
    }

    @Bean
    @Order(1)
    @ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
    SecurityFilterChain summerAuthorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();
        MediaTypeRequestMatcher htmlMatcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        htmlMatcher.setIgnoredMediaTypes(Set.of(MediaType.ALL));

        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, server -> server.oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        htmlMatcher
                ))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
        return http.build();
    }

    @Bean
    @Order(2)
    @ConditionalOnProperty(prefix = "summer.security.resource-server", name = "enabled", havingValue = "true")
    SecurityFilterChain summerResourceServerSecurityFilterChain(
            HttpSecurity http,
            SummerSecurityProperties properties,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        String[] protectedPaths = properties.getResourceServer().getProtectedPaths().toArray(String[]::new);
        http.securityMatcher(protectedPaths)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    @Order(3)
    @ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
    SecurityFilterChain summerLoginSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .formLogin(Customizer.withDefaults());
        return http.build();
    }

    private static RegisteredClient registeredClient(
            SummerSecurityProperties.Client configured,
            PasswordEncoder passwordEncoder
    ) {
        if (configured.getId() == null || configured.getSecret() == null) {
            throw new IllegalStateException("OAuth2 client id and secret are required");
        }
        if (configured.getId().isBlank()) {
            throw new IllegalStateException("OAuth2 client id must not be blank");
        }
        if (configured.getSecret().isBlank()) {
            throw new IllegalStateException("OAuth2 client secret must not be blank");
        }

        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(configured.getId())
                .clientSecret(passwordEncoder.encode(configured.getSecret()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build());

        configured.getGrantTypes().forEach(grantType -> builder.authorizationGrantType(grantType(grantType)));
        configured.getRedirectUris().forEach(builder::redirectUri);
        configured.getScopes().forEach(builder::scope);
        return builder.build();
    }

    private static AuthorizationGrantType grantType(String value) {
        return switch (value) {
            case "authorization_code" -> AuthorizationGrantType.AUTHORIZATION_CODE;
            case "client_credentials" -> AuthorizationGrantType.CLIENT_CREDENTIALS;
            case "refresh_token" -> AuthorizationGrantType.REFRESH_TOKEN;
            default -> new AuthorizationGrantType(value);
        };
    }
}
