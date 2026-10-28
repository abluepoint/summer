package com.abluepoint.summer.sample.key.infrastructure.persistence;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "summer.security.storage.keys=mysql")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MysqlKeyFailureAuditTest {
    private static final String REQUEST_ID_ATTRIBUTE = "com.abluepoint.summer.requestId";
    @DynamicPropertySource
    static void keyProperties(DynamicPropertyRegistry properties) {
        String master = UUID.randomUUID().toString();
        properties.add("summer.sample.security.master-key", () -> master);
    }

    @Autowired MysqlSummerKeyStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext context;
    private final Logger logger = (Logger) LoggerFactory.getLogger("com.abluepoint.summer.security.audit");
    private ListAppender<ILoggingEvent> events;
    private Object previousRequestId;

    @BeforeEach
    void captureAudit() {
        events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        MDC.put("requestId", "key-fault-request");
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null) {
            previousRequestId = request.getAttribute(REQUEST_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            request.setAttribute(REQUEST_ID_ATTRIBUTE, "key-fault-request", RequestAttributes.SCOPE_REQUEST);
        }
    }

    @AfterEach
    void stopCapture() {
        MDC.remove("requestId");
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null) {
            if (previousRequestId == null) request.removeAttribute(REQUEST_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
            else request.setAttribute(REQUEST_ID_ATTRIBUTE, previousRequestId, RequestAttributes.SCOPE_REQUEST);
        }
        logger.detachAppender(events);
        events.stop();
    }

    @Test
    void successfulSigningUsesTheConfiguredEncoderWithoutFailureAudit() {
        JwtEncoder encoder = context.getBean(JwtEncoder.class);
        String token = encoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder().subject("key-audit-test").build()))
                .getTokenValue();
        assertThat(context.getBean(JwtDecoder.class).decode(token).getSubject()).isEqualTo("key-audit-test");
        assertThat(events.list.isEmpty()).isTrue();
    }

    @Test
    void auditsDecryptionFailureWithoutRecordingStoredMaterial() throws Exception {
        String kid = jdbc.queryForObject("SELECT kid FROM summer_security_keys WHERE status='ACTIVE'", String.class);
        String ciphertext = jdbc.queryForObject("SELECT ciphertext FROM summer_security_keys WHERE kid=?", String.class, kid);
        String sensitive = "invalid-ciphertext-" + UUID.randomUUID();
        boolean failed = false;
        try {
            jdbc.update("UPDATE summer_security_keys SET ciphertext=? WHERE kid=?", sensitive, kid);
            store.get(new JWKSelector(new JWKMatcher.Builder().build()), null);
        } catch (RuntimeException expected) {
            failed = true;
        } finally {
            jdbc.update("UPDATE summer_security_keys SET ciphertext=? WHERE kid=?", ciphertext, kid);
        }
        assertThat(failed).isTrue();
        assertThat(events.list.stream().anyMatch(e -> e.getFormattedMessage().contains("security_event=key_read_failure"))).isTrue();
        assertThat(events.list.stream().noneMatch(e -> e.getFormattedMessage().contains(sensitive)
                || e.getFormattedMessage().contains(ciphertext) || e.getThrowableProxy() != null)).isTrue();
    }

    @Test
    void auditsRealEncoderFailureWithoutLoggingClaimsOrExceptionContent() {
        String sensitiveClaim = UUID.randomUUID().toString();
        JwtEncoder encoder = context.getBeanProvider(JwtEncoder.class).getIfAvailable(() -> new NimbusJwtEncoder(store));
        boolean failed = false;
        try {
            encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                    JwtClaimsSet.builder().subject(sensitiveClaim).build()));
        } catch (RuntimeException expected) {
            failed = true;
        }
        assertThat(failed).isTrue();
        assertThat(events.list.stream().anyMatch(e -> e.getFormattedMessage().contains("security_event=key_sign_failure")
                && e.getFormattedMessage().contains("requestId=key-fault-request"))).isTrue();
        assertThat(events.list.stream().noneMatch(e -> e.getFormattedMessage().contains(sensitiveClaim)
                || e.getThrowableProxy() != null)).isTrue();
    }

    @Test
    void auditsRealKeyTableReadFailureAndRecoversWithoutLoggingSuccessfulReads() throws Exception {
        jdbc.execute("ALTER TABLE summer_security_keys RENAME TO summer_security_keys_fault");
        boolean failed = false;
        boolean metadataFailed = false;
        try {
            try {
                store.get(new JWKSelector(new JWKMatcher.Builder().build()), null);
            } catch (RuntimeException expected) {
                failed = true;
            }
            try {
                store.metadata();
            } catch (RuntimeException expected) {
                metadataFailed = true;
            }
        } finally {
            jdbc.execute("ALTER TABLE summer_security_keys_fault RENAME TO summer_security_keys");
        }
        assertThat(failed).isTrue();
        assertThat(metadataFailed).isTrue();
        assertThat(events.list.stream().anyMatch(e -> e.getFormattedMessage().contains("security_event=key_read_failure")
                && e.getFormattedMessage().contains("requestId=key-fault-request"))).isTrue();
        int failures = events.list.size();
        assertThat(store.get(new JWKSelector(new JWKMatcher.Builder().build()), null).isEmpty()).isFalse();
        assertThat(events.list.size()).isEqualTo(failures);
    }
}
