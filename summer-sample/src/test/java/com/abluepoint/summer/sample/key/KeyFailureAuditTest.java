package com.abluepoint.summer.sample.key;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KeyFailureAuditTest {
    @Test
    void auditSinkFailureDoesNotReplaceTheOriginalSigningFailure() {
        var original = new JwtEncodingException(UUID.randomUUID().toString());
        KeyFailureAudit audit = new KeyFailureAudit((event, principal, attributes) -> {
            throw new IllegalStateException(UUID.randomUUID().toString());
        }, () -> "request-sign-failure");
        var encoder = new AuditedKeyJwtEncoder(parameters -> { throw original; }, audit);
        boolean originalPreserved = false;
        try {
            encoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder().subject("test").build()));
        } catch (RuntimeException failure) {
            originalPreserved = failure == original;
        }
        assertThat(originalPreserved).isTrue();
    }

    @Test
    void retainsOnlyTheDeclaredSafeMetadata() {
        List<Map<String, ?>> events = new ArrayList<>();
        String kid = UUID.randomUUID().toString();
        KeyFailureAudit audit = new KeyFailureAudit((event, principal, attributes) -> {
            assertThat(event).isEqualTo("key_read_failure");
            assertThat(principal).isEqualTo("system");
            events.add(attributes);
        }, () -> "request-key-1");

        audit.readFailed(kid);

        assertThat(events.size()).isEqualTo(1);
        assertThat(events.getFirst().keySet()).containsExactlyInAnyOrder("operation", "failureCategory", "keyId", "requestId");
        assertThat(events.getFirst().get("keyId").equals(kid)).isTrue();
        assertThat(events.getFirst().get("requestId")).isEqualTo("request-key-1");
    }

    @Test
    void rejectsArbitraryKeyIdsAndUnsafeCorrelationValues() {
        List<Map<String, ?>> events = new ArrayList<>();
        String sensitive = "private-material-" + UUID.randomUUID();
        KeyFailureAudit audit = new KeyFailureAudit((event, principal, attributes) -> events.add(attributes),
                () -> sensitive + "\r\n");

        audit.readFailed(sensitive);

        assertThat(events.size()).isEqualTo(1);
        assertThat(events.toString().contains(sensitive)).isFalse();
        assertThat(events.getFirst().get("keyId")).isEqualTo("unavailable");
        assertThat(events.getFirst().get("requestId")).isEqualTo("unbound");
    }

    @Test
    void fallsBackToExistingLogChannelWhenTheAuditSinkFails() {
        Logger logger = (Logger) LoggerFactory.getLogger("com.abluepoint.summer.security.audit");
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        String sensitive = UUID.randomUUID().toString();
        try {
            KeyFailureAudit audit = new KeyFailureAudit((event, principal, attributes) -> {
                throw new IllegalStateException(sensitive);
            }, () -> "request-fallback");

            audit.signFailed();

            assertThat(events.list.size()).isEqualTo(1);
            String output = events.list.getFirst().getFormattedMessage();
            assertThat(output.contains("security_event=key_sign_failure")
                    && output.contains("requestId=request-fallback") && !output.contains(sensitive)).isTrue();
            assertThat(events.list.getFirst().getThrowableProxy() == null).isTrue();
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    @Test
    void keepsAuditingWhenRequestCorrelationIsUnavailable() {
        List<Map<String, ?>> events = new ArrayList<>();
        KeyFailureAudit audit = new KeyFailureAudit((event, principal, attributes) -> events.add(attributes),
                () -> { throw new IllegalStateException(); });

        audit.signFailed();

        assertThat(events.size()).isEqualTo(1);
        assertThat(events.getFirst().get("requestId")).isEqualTo("unbound");
    }
}
