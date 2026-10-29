package com.abluepoint.summer.sample.authorization.infrastructure;

import com.abluepoint.summer.sample.authorization.api.OAuthClientManagementController;
import com.abluepoint.summer.sample.authorization.application.OAuthClientManagementService;
import com.abluepoint.summer.sample.authorization.domain.OAuthClientManagementStore;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import com.abluepoint.summer.security.audit.spi.SecurityAuditLogger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import com.abluepoint.summer.security.oauth2.spi.SummerAuthorizationStore;
import com.abluepoint.summer.security.oauth2.spi.SummerConsentStore;
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
public class JdbcOAuthStorageConfiguration {
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

    @Bean
    @ConditionalOnBean({JdbcSummerOAuthClientStore.class, SummerConsentStore.class})
    @ConditionalOnMissingBean(OAuthClientManagementService.class)
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "consent", havingValue = "jdbc")
    OAuthClientManagementService oauthClientManagementService(OAuthClientManagementStore clients,
                                                              SummerConsentStore consents, SecurityAuditLogger audit) {
        return new OAuthClientManagementService(clients, consents, audit);
    }

    // Component scan can encounter the API before storage/service definitions after package moves.
    // Register the controller after its use case without introducing a second instance.
    @Bean
    @ConditionalOnBean(OAuthClientManagementService.class)
    @ConditionalOnMissingBean(OAuthClientManagementController.class)
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "consent", havingValue = "jdbc")
    OAuthClientManagementController oauthClientManagementController(OAuthClientManagementService management,
                                                                   RequestIdAccessor requestId) {
        return new OAuthClientManagementController(management, requestId);
    }
}
