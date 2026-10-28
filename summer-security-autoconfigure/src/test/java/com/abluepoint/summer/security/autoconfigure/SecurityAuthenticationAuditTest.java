package com.abluepoint.summer.security.autoconfigure;

import com.abluepoint.summer.security.audit.spi.SecurityAuditLogger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
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
    void neverUsesAnUnverifiedBearerCredentialAsTheAuditPrincipal() {
        var auditLogger = new RecordingSecurityAuditLogger();
        String credential = java.util.UUID.randomUUID().toString();
        contextRunner.withBean(SecurityAuditLogger.class, () -> auditLogger).run(context -> {
            context.publishEvent(new AuthenticationFailureBadCredentialsEvent(
                    new BearerTokenAuthenticationToken(credential),
                    new BadCredentialsException(credential)));
            assertThat(auditLogger.events.size()).isEqualTo(1);
            AuditEvent audit = auditLogger.events.getFirst();
            // Assert booleans so a regression never writes the credential into a test report.
            assertThat(audit.principal().equals("anonymous") && !audit.toString().contains(credential))
                    .as("unverified bearer and exception content must not enter the audit output").isTrue();
        });
    }

    @Test
    void doesNotTreatUnknownPrincipalObjectsAsSafeIdentifiers() {
        var auditLogger = new RecordingSecurityAuditLogger();
        String credential = java.util.UUID.randomUUID().toString();
        contextRunner.withBean(SecurityAuditLogger.class, () -> auditLogger).run(context -> {
            context.publishEvent(new AuthenticationFailureBadCredentialsEvent(
                    new TestingAuthenticationToken(credential, credential), new BadCredentialsException(credential)));
            assertThat(auditLogger.events.size()).isEqualTo(1);
            assertThat(auditLogger.events.getFirst().principal().equals("anonymous")
                    && !auditLogger.events.toString().contains(credential)).isTrue();
        });
    }

    @Test
    void keepsTheVerifiedJwtSubjectWithoutRecordingItsTokenValue() {
        var auditLogger = new RecordingSecurityAuditLogger();
        String credential = java.util.UUID.randomUUID().toString();
        Jwt jwt = Jwt.withTokenValue(credential).header("alg", "RS256").subject("verified-user").build();
        contextRunner.withBean(SecurityAuditLogger.class, () -> auditLogger).run(context -> {
            context.publishEvent(new AuthenticationSuccessEvent(new JwtAuthenticationToken(jwt, List.of())));
            assertThat(auditLogger.events.size()).isEqualTo(1);
            assertThat(auditLogger.events.getFirst().principal().equals("verified-user")
                    && !auditLogger.events.toString().contains(credential)).isTrue();
        });
    }

    @Test
    void preventsControlCharactersInUsernameAuditIdentifiers() {
        var auditLogger = new RecordingSecurityAuditLogger();
        String credential = java.util.UUID.randomUUID().toString();
        contextRunner.withBean(SecurityAuditLogger.class, () -> auditLogger).run(context -> {
            context.publishEvent(new AuthenticationFailureBadCredentialsEvent(
                    UsernamePasswordAuthenticationToken.unauthenticated("operator\r\n\tname", credential),
                    new BadCredentialsException(credential)));
            assertThat(auditLogger.events.size()).isEqualTo(1);
            assertThat(auditLogger.events.getFirst().principal().equals("operator___name")
                    && !auditLogger.events.toString().contains(credential)).isTrue();
        });
    }

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

    @Test
    void rejectsAnEmptyResourceServerPathList() {
        contextRunner
                .withUserConfiguration(TestWebSecurityConfiguration.class)
                .withPropertyValues(
                        "summer.security.resource-server.enabled=true",
                        "summer.security.resource-server.protected-paths="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("At least one summer.security.resource-server.protected-paths entry is required");
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
