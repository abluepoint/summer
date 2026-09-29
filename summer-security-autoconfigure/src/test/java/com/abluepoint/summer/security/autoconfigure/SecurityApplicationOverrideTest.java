package com.abluepoint.summer.security.autoconfigure;

import org.junit.jupiter.api.Test;
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
