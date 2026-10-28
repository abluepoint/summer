package com.abluepoint.summer.security.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import tools.jackson.databind.ObjectMapper;
import com.abluepoint.summer.security.oauth2.spi.SummerConsentStore;
import com.abluepoint.summer.security.session.spi.SummerSessionStore;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityStorageContractTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SummerSecurityAutoConfiguration.class))
            .withUserConfiguration(BaselineConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues(
                    "summer.security.authorization-server.enabled=true",
                    "summer.security.authorization-server.clients[0].id=test-client",
                    "summer.security.authorization-server.clients[0].secret=test-secret",
                    "summer.security.authorization-server.clients[0].grant-types=client_credentials"
            );

    @Test
    void exposesDefaultConsentAndSessionStorageContracts() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(OAuth2AuthorizationConsentService.class)).hasSize(1);
            assertThat(context.getBean(OAuth2AuthorizationConsentService.class))
                    .isInstanceOf(SummerConsentStore.class);
            assertThat(context.getBean(SecurityContextRepository.class))
                    .isInstanceOf(SummerSessionStore.class)
                    .isInstanceOf(HttpSessionSecurityContextRepository.class);
            assertThat(context.getBean(RegisteredClientRepository.class)).isNotNull();
            assertThat(context.getBean(SummerSecurityProperties.class).getStorage().getIdentity())
                    .isEqualTo("memory");
        });
    }

    @Test
    void rejectsCustomStorageSelectionWithoutAnApplicationAdapter() {
        contextRunner
                .withPropertyValues("summer.security.storage.identity=custom")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("custom identity storage");
                });
    }

    @Test
    void usesApplicationAdaptersWhenEveryStorageIsConfiguredAsCustom() {
        contextRunner
                .withUserConfiguration(CustomStorageConfiguration.class)
                .withPropertyValues(
                        "summer.security.storage.identity=custom",
                        "summer.security.storage.oauth-client=custom",
                        "summer.security.storage.authorization=custom",
                        "summer.security.storage.consent=custom",
                        "summer.security.storage.session=custom",
                        "summer.security.storage.keys=custom"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(UserDetailsService.class))
                            .isSameAs(context.getBean("customIdentityStore"));
                    assertThat(context.getBean(RegisteredClientRepository.class))
                            .isSameAs(context.getBean("customOauthClientStore"));
                    assertThat(context.getBean(OAuth2AuthorizationService.class))
                            .isSameAs(context.getBean("customAuthorizationStore"));
                    assertThat(context.getBean(OAuth2AuthorizationConsentService.class))
                            .isSameAs(context.getBean("customConsentStore"));
                    assertThat(context.getBean(SecurityContextRepository.class))
                            .isSameAs(context.getBean("customSessionStore"));
                    assertThat(context.getBean(JWKSource.class))
                            .isSameAs(context.getBean("customKeyStore"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class BaselineConfiguration {

        @Bean
        Object baselineMarker() {
            return new Object();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomStorageConfiguration {

        @Bean
        UserDetailsService customIdentityStore() {
            return username -> User.withUsername(username).password("{noop}test").roles("USER").build();
        }

        @Bean
        RegisteredClientRepository customOauthClientStore() {
            RegisteredClient client = RegisteredClient.withId("custom-client")
                    .clientId("test-client")
                    .clientSecret("{noop}test-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .scope("customer.read")
                    .build();
            return new InMemoryRegisteredClientRepository(client);
        }

        @Bean
        OAuth2AuthorizationService customAuthorizationStore() {
            return new InMemoryOAuth2AuthorizationService();
        }

        @Bean
        OAuth2AuthorizationConsentService customConsentStore() {
            return new InMemoryOAuth2AuthorizationConsentService();
        }

        @Bean
        SecurityContextRepository customSessionStore() {
            return new HttpSessionSecurityContextRepository();
        }

        @Bean
        JWKSource<SecurityContext> customKeyStore() {
            return (selector, context) -> java.util.List.of();
        }
    }
}
