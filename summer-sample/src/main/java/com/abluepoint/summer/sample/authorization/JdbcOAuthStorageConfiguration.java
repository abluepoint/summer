package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.security.autoconfigure.storage.SummerAuthorizationStore;
import com.abluepoint.summer.security.autoconfigure.storage.SummerConsentStore;
import com.abluepoint.summer.security.autoconfigure.storage.SummerOAuthClientStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "summer.security.authorization-server", name = "enabled", havingValue = "true")
class JdbcOAuthStorageConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "oauth-client", havingValue = "jdbc")
    JdbcSummerOAuthClientStore jdbcSummerOAuthClientStore(JdbcTemplate jdbcTemplate) {
        return new JdbcSummerOAuthClientStore(jdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "authorization", havingValue = "jdbc")
    SummerAuthorizationStore jdbcSummerAuthorizationStore(JdbcOperations jdbcOperations,
                                                          RegisteredClientRepository clients) {
        return new JdbcSummerAuthorizationStore(jdbcOperations, clients);
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "consent", havingValue = "jdbc")
    SummerConsentStore jdbcSummerConsentStore(JdbcOperations jdbcOperations,
                                               RegisteredClientRepository clients) {
        return new JdbcSummerConsentStore(jdbcOperations, clients);
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "oauth-client", havingValue = "jdbc")
    @ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
    ApplicationRunner seedConfiguredClients(com.abluepoint.summer.security.autoconfigure.SummerSecurityProperties properties,
                                            JdbcSummerOAuthClientStore clients,
                                            PasswordEncoder passwordEncoder) {
        return args -> properties.getAuthorizationServer().getClients().forEach(configured -> {
            if (clients.isRegistered(configured.getId())) return;
            RegisteredClient.Builder builder = RegisteredClient.withId(java.util.UUID.randomUUID().toString())
                    .clientId(configured.getId()).clientName(configured.getId())
                    .clientSettings(ClientSettings.builder().requireProofKey(configured.isPublicClient()).requireAuthorizationConsent(false).build())
                    .tokenSettings(TokenSettings.builder().authorizationCodeTimeToLive(Duration.ofMinutes(5)).accessTokenTimeToLive(Duration.ofMinutes(15)).build());
            if (configured.isPublicClient()) builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
            else {
                builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
                if (configured.getSecret() != null) builder.clientSecret(passwordEncoder.encode(configured.getSecret()));
            }
            configured.getGrantTypes().forEach(value -> builder.authorizationGrantType(new AuthorizationGrantType(value)));
            configured.getRedirectUris().forEach(builder::redirectUri);
            configured.getPostLogoutRedirectUris().forEach(builder::postLogoutRedirectUri);
            configured.getScopes().forEach(builder::scope);
            clients.save(builder.build());
        });
    }

}
