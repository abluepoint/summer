package com.abluepoint.summer.security.autoconfigure;

import com.abluepoint.summer.security.audit.SecurityAuditLogger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuthenticationAuditTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SummerSecurityAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void recordsDisabledAccountAsAuthenticationFailure() {
        RecordingSecurityAuditLogger auditLogger = new RecordingSecurityAuditLogger();

        contextRunner
                .withBean(SecurityAuditLogger.class, () -> auditLogger)
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    var authentication = UsernamePasswordAuthenticationToken.unauthenticated("disabled-user", "secret");
                    context.publishEvent(new AuthenticationFailureDisabledEvent(
                            authentication,
                            new DisabledException("Account is disabled")
                    ));

                    assertThat(auditLogger.events).containsExactly(new AuditEvent(
                            "authentication_failure",
                            "disabled-user",
                            Map.of("authenticationType", "UsernamePasswordAuthenticationToken")
                    ));
                });
    }

    @Test
    void rejectsBlankAuthorizationServerClientSecret() {
        contextRunner
                .withUserConfiguration(TestWebSecurityConfiguration.class)
                .withPropertyValues(
                        "summer.security.authorization-server.enabled=true",
                        "summer.security.authorization-server.clients[0].id=test-client",
                        "summer.security.authorization-server.clients[0].secret="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("OAuth2 client secret must not be blank");
                });
    }

    @Test
    void rejectsBlankAuthorizationServerClientId() {
        contextRunner
                .withUserConfiguration(TestWebSecurityConfiguration.class)
                .withPropertyValues(
                        "summer.security.authorization-server.enabled=true",
                        "summer.security.authorization-server.clients[0].id=",
                        "summer.security.authorization-server.clients[0].secret=test-secret"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("OAuth2 client id must not be blank");
                });
    }

    private record AuditEvent(String event, String principal, Map<String, ?> attributes) {
    }

    private static final class RecordingSecurityAuditLogger implements SecurityAuditLogger {
        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void record(String event, String principal, Map<String, ?> attributes) {
            events.add(new AuditEvent(event, principal, attributes));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestWebSecurityConfiguration {
    }
}
