package com.abluepoint.summer.security.autoconfigure;

import com.abluepoint.summer.security.audit.SecurityAuditLogger;
import com.abluepoint.summer.security.audit.Slf4jSecurityAuditLogger;
import com.abluepoint.summer.security.web.JsonAccessDeniedHandler;
import com.abluepoint.summer.security.web.JsonAuthenticationEntryPoint;
import com.abluepoint.summer.security.autoconfigure.storage.DefaultSummerAuthorizationStore;
import com.abluepoint.summer.security.autoconfigure.storage.DefaultSummerConsentStore;
import com.abluepoint.summer.security.autoconfigure.storage.DefaultSummerIdentityStore;
import com.abluepoint.summer.security.autoconfigure.storage.DefaultSummerKeyStore;
import com.abluepoint.summer.security.autoconfigure.storage.DefaultSummerOAuthClientStore;
import com.abluepoint.summer.security.autoconfigure.storage.DefaultSummerSessionStore;
import com.abluepoint.summer.security.autoconfigure.storage.SummerAuthorizationStore;
import com.abluepoint.summer.security.autoconfigure.storage.SummerConsentStore;
import com.abluepoint.summer.security.autoconfigure.storage.SummerIdentityStore;
import com.abluepoint.summer.security.autoconfigure.storage.SummerKeyStore;
import com.abluepoint.summer.security.autoconfigure.storage.SummerOAuthClientStore;
import com.abluepoint.summer.security.autoconfigure.storage.SummerSessionStore;
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
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.oidc.web.authentication.OidcLogoutAuthenticationSuccessHandler;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@AutoConfiguration
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
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
    SummerCsrfEndpoint summerCsrfEndpoint() {
        return new SummerCsrfEndpoint();
    }

    @Bean
    @ConditionalOnMissingBean
    PasswordEncoder summerPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "oauth-client", havingValue = "memory", matchIfMissing = true)
    @ConditionalOnMissingBean(RegisteredClientRepository.class)
    SummerOAuthClientStore summerRegisteredClientRepository(
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
        return new DefaultSummerOAuthClientStore(new InMemoryRegisteredClientRepository(clients));
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "authorization", havingValue = "memory", matchIfMissing = true)
    @ConditionalOnMissingBean(OAuth2AuthorizationService.class)
    SummerAuthorizationStore summerAuthorizationService() {
        return new DefaultSummerAuthorizationStore(new InMemoryOAuth2AuthorizationService());
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "consent", havingValue = "memory", matchIfMissing = true)
    @ConditionalOnMissingBean(OAuth2AuthorizationConsentService.class)
    SummerConsentStore summerAuthorizationConsentService() {
        return new DefaultSummerConsentStore(new InMemoryOAuth2AuthorizationConsentService());
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "identity", havingValue = "memory", matchIfMissing = true)
    @ConditionalOnMissingBean(UserDetailsService.class)
    SummerIdentityStore summerUserDetailsService(SummerSecurityProperties properties, PasswordEncoder passwordEncoder) {
        SummerSecurityProperties.User configured = properties.getAuthorizationServer().getUser();
        if (configured.getUsername() == null || configured.getPassword() == null) {
            return new DefaultSummerIdentityStore(new InMemoryUserDetailsManager());
        }
        return new DefaultSummerIdentityStore(new InMemoryUserDetailsManager(User.withUsername(configured.getUsername())
                .password(passwordEncoder.encode(configured.getPassword()))
                .roles("USER")
                .build()));
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(OAuth2TokenCustomizer.class)
    OAuth2TokenCustomizer<JwtEncodingContext> summerJwtTokenCustomizer() {
        return context -> {
            RegisteredClient client = context.getRegisteredClient();
            boolean publicClient = client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE);
            if (!publicClient || context.getTokenType() == null) {
                return;
            }
            String tokenType = context.getTokenType().getValue();
            if (OidcParameterNames.ID_TOKEN.equals(tokenType)) {
                String username = context.getPrincipal().getName();
                context.getClaims()
                        .claim("preferred_username", username)
                        .claim("name", username)
                        .expiresAt(Instant.now().plus(Duration.ofMinutes(15)));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "keys", havingValue = "memory", matchIfMissing = true)
    @ConditionalOnMissingBean(JWKSource.class)
    SummerKeyStore summerJwkSource() {
        try {
            RSAKey rsaKey = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
            JWKSet jwkSet = new JWKSet(rsaKey);
            return new DefaultSummerKeyStore(jwkSet);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate authorization server signing key", exception);
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "session", havingValue = "memory", matchIfMissing = true)
    @ConditionalOnMissingBean(SecurityContextRepository.class)
    SummerSessionStore summerSessionStore() {
        return new DefaultSummerSessionStore();
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
    SecurityStorageSelectionValidator summerSecurityStorageSelectionValidator(
            SummerSecurityProperties properties,
            ObjectProvider<UserDetailsService> identityStores,
            ObjectProvider<RegisteredClientRepository> oauthClientStores,
            ObjectProvider<OAuth2AuthorizationService> authorizationStores,
            ObjectProvider<OAuth2AuthorizationConsentService> consentStores,
            ObjectProvider<SecurityContextRepository> sessionStores,
            ObjectProvider<JWKSource<SecurityContext>> keyStores
    ) {
        return new SecurityStorageSelectionValidator(properties, identityStores, oauthClientStores,
                authorizationStores, consentStores, sessionStores, keyStores);
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
    SecurityFilterChain summerAuthorizationServerSecurityFilterChain(
            HttpSecurity http,
            SummerSecurityProperties properties,
            SecurityContextRepository securityContextRepository
    ) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();
        String loginPage = properties.getAuthorizationServer().getLoginPage();
        MediaTypeRequestMatcher htmlMatcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        htmlMatcher.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        OidcLogoutAuthenticationSuccessHandler logoutSuccessHandler = new OidcLogoutAuthenticationSuccessHandler();
        SecurityContextLogoutHandler securityContextLogoutHandler = new SecurityContextLogoutHandler();
        CookieClearingLogoutHandler cookieClearingLogoutHandler = new CookieClearingLogoutHandler("JSESSIONID");
        logoutSuccessHandler.setLogoutHandler((request, response, authentication) -> {
            OidcLogoutAuthenticationToken oidcLogout = (OidcLogoutAuthenticationToken) authentication;
            if (oidcLogout.isPrincipalAuthenticated()) {
                securityContextLogoutHandler.logout(request, response,
                        (Authentication) oidcLogout.getPrincipal());
            }
            cookieClearingLogoutHandler.logout(request, response, authentication);
        });

        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, server -> server.oidc(oidc -> oidc
                        .logoutEndpoint(logout -> logout.logoutResponseHandler(logoutSuccessHandler))))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint(loginPage),
                        htmlMatcher
                ))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
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
        if (protectedPaths.length == 0) {
            throw new IllegalStateException("At least one summer.security.resource-server.protected-paths entry is required");
        }
        http.securityMatcher(protectedPaths)
                .authorizeHttpRequests(authorize -> authorize
                        // Logout remains idempotent when a Product Session has expired.
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/session").permitAll()
                        .anyRequest().authenticated())
                // Keep the resource server bearer-token path compatible with the
                // local form-login session used by the sample console. A bearer
                // token remains supported, while a successful /login can also
                // establish the normal servlet SecurityContext session.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
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
    @ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
    WebMvcConfigurer summerLoginPageRedirectConfiguration(SummerSecurityProperties properties) {
        String loginPage = properties.getAuthorizationServer().getLoginPage();
        if (!isAbsoluteUrl(loginPage)) {
            return new WebMvcConfigurer() {
            };
        }
        String loginPath = loginPagePath(loginPage);
        return new WebMvcConfigurer() {
            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                registry.addRedirectViewController(loginPath, loginPage);
            }
        };
    }

    @Bean
    @Order(3)
    @ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
    SecurityFilterChain summerLoginSecurityFilterChain(
            HttpSecurity http,
            SummerSecurityProperties properties,
            ObjectProvider<AuthenticationSuccessHandler> successHandlers,
            SecurityContextRepository securityContextRepository
    ) throws Exception {
        String loginPage = properties.getAuthorizationServer().getLoginPage();
        AuthenticationSuccessHandler successHandler = successHandlers.orderedStream()
                .findFirst()
                .orElseGet(() -> savedRequestAwareSuccessHandler(loginPage));
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage(loginPagePath(loginPage))
                        .loginProcessingUrl("/login")
                        .successHandler(successHandler))
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.changeSessionId())
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .logout(logout -> logout
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl("/login?logout"));
        return http.build();
    }

    private static SavedRequestAwareAuthenticationSuccessHandler savedRequestAwareSuccessHandler(String loginPage) {
        SavedRequestAwareAuthenticationSuccessHandler handler =
                new SavedRequestAwareAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl(defaultWorkspaceUrl(loginPage));
        return handler;
    }

    private static String defaultWorkspaceUrl(String loginPage) {
        if (!isAbsoluteUrl(loginPage)) {
            return "/dashboard/workplace";
        }
        try {
            return URI.create(loginPage).resolve("/dashboard/workplace").toString();
        } catch (IllegalArgumentException ignored) {
            return "/dashboard/workplace";
        }
    }

    private static boolean isAbsoluteUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            return URI.create(value).isAbsolute();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String loginPagePath(String loginPage) {
        if (loginPage == null || loginPage.isBlank()) {
            return "/login";
        }
        try {
            URI uri = URI.create(loginPage);
            if (uri.isAbsolute()) {
                String path = uri.getPath();
                return path == null || path.isBlank() ? "/" : path;
            }
        } catch (IllegalArgumentException ignored) {
            // Keep the original value for Spring Security to validate and report.
        }
        return loginPage.startsWith("/") ? loginPage : "/" + loginPage;
    }

    private static RegisteredClient registeredClient(
            SummerSecurityProperties.Client configured,
            PasswordEncoder passwordEncoder
    ) {
        if (configured.getId() == null || configured.getId().isBlank()) {
            throw new IllegalStateException("OAuth2 client id must not be blank");
        }
        boolean publicClient = configured.isPublicClient()
                || ClientAuthenticationMethod.NONE.getValue().equalsIgnoreCase(configured.getClientAuthenticationMethod());
        if ("summer-ui".equals(configured.getId()) && !publicClient) {
            throw new IllegalStateException("summer-ui must be a public client");
        }
        if (publicClient) {
            if (configured.getSecret() != null && !configured.getSecret().isBlank()) {
                throw new IllegalStateException("Public OAuth2 client must not define a client secret");
            }
            if (configured.getGrantTypes().size() != 1
                    || !configured.getGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE.getValue())) {
                throw new IllegalStateException("Public OAuth2 client supports authorization_code only");
            }
            if (configured.getRedirectUris().isEmpty()) {
                throw new IllegalStateException("Public OAuth2 client requires at least one redirect URI");
            }
            if ("summer-ui".equals(configured.getId())
                    && !Set.copyOf(configured.getScopes())
                    .equals(Set.of("openid", "profile", "customer.read", "customer.write"))) {
                throw new IllegalStateException("summer-ui scopes must be exactly openid, profile, customer.read and customer.write");
            }
        } else {
            if (configured.getSecret() == null || configured.getSecret().isBlank()) {
                throw new IllegalStateException("OAuth2 client secret must not be blank");
            }
        }

        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(configured.getId())
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(publicClient)
                        .build());
        if (publicClient) {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .tokenSettings(TokenSettings.builder()
                            .authorizationCodeTimeToLive(Duration.ofMinutes(5))
                            .accessTokenTimeToLive(Duration.ofMinutes(15))
                            .build());
        } else {
            builder.clientSecret(passwordEncoder.encode(configured.getSecret()))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        }

        configured.getGrantTypes().forEach(grantType -> builder.authorizationGrantType(grantType(grantType)));
        configured.getRedirectUris().forEach(builder::redirectUri);
        configured.getPostLogoutRedirectUris().forEach(builder::postLogoutRedirectUri);
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
