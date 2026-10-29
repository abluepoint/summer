package com.abluepoint.summer.sample.authorization.infrastructure;

import com.abluepoint.summer.sample.authorization.api.OAuthClientManagementController;
import com.abluepoint.summer.sample.authorization.application.OAuthClientManagementService;
import com.abluepoint.summer.sample.authorization.domain.OAuthClientManagementStore;
import com.abluepoint.summer.security.oauth2.spi.SummerConsentStore;
import com.abluepoint.summer.security.audit.spi.SecurityAuditLogger;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class OAuthManagementAssemblyTest {
    // Composition only: no SQL is executed. HTTP coverage uses the existing H2 fixture.
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(JdbcTemplate.class, () -> new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:oauth-assembly")))
            .withBean(RequestIdAccessor.class, () -> () -> "assembly-request")
            .withBean(SecurityAuditLogger.class, () -> (event, principal, attributes) -> {})
            .withPropertyValues("spring.flyway.enabled=false", "summer.security.storage.oauth-client=jdbc");

    @Test void authorizationServerRemainsOptional() {
        runner.withUserConfiguration(JdbcOAuthStorageConfiguration.class, OAuthClientManagementController.class)
                .withPropertyValues("summer.security.authorization-server.enabled=false", "summer.security.storage.consent=jdbc")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OAuthClientManagementService.class);
                    assertThat(context).doesNotHaveBean(OAuthClientManagementController.class);
                });
    }

    @Test void managementRequiresItsExistingJdbcConsentMode() {
        runner.withUserConfiguration(JdbcOAuthStorageConfiguration.class, OAuthClientManagementController.class)
                .withPropertyValues("summer.security.authorization-server.enabled=true", "summer.security.storage.consent=memory")
                .run(context -> {
                    assertThat(context).hasSingleBean(JdbcSummerOAuthClientStore.class);
                    assertThat(context).doesNotHaveBean(OAuthClientManagementService.class);
                    assertThat(context).doesNotHaveBean(OAuthClientManagementController.class);
                });
    }

    @Test void applicationProvidedUseCaseIsRetainedAndControllerIsRegisteredOnce() {
        runner.withUserConfiguration(ApplicationUseCase.class, JdbcOAuthStorageConfiguration.class, OAuthClientManagementController.class)
                .withPropertyValues("summer.security.authorization-server.enabled=true", "summer.security.storage.consent=jdbc")
                .run(context -> {
                    assertThat(context).hasSingleBean(OAuthClientManagementService.class);
                    assertThat(context).hasSingleBean(OAuthClientManagementController.class);
                    assertThat(context.getBean(OAuthClientManagementService.class)).isSameAs(context.getBean("applicationOAuthManagement"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationUseCase {
        @Bean OAuthClientManagementService applicationOAuthManagement(OAuthClientManagementStore clients,
                                                                       SummerConsentStore consents, SecurityAuditLogger audit) {
            return new OAuthClientManagementService(clients, consents, audit);
        }
    }
}
