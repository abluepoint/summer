package com.abluepoint.summer.sample.key;

import com.abluepoint.summer.security.audit.SecurityAuditLogger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "summer.security.storage.keys=mysql")
@AutoConfigureMockMvc
@Import({KeyRotationFailureHttpTest.AuditConfiguration.class, com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class KeyRotationFailureHttpTest {
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    @org.junit.jupiter.api.BeforeEach
    void grantExactCurrentOperations() {
        for (String subject : java.util.List.of("user", "key-admin"))
            current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), subject,
                    (handler, path, method) -> handler.getBeanType().getName().equals("com.abluepoint.summer.sample.key.KeyCapabilityController")
                            && java.util.Set.of("/api/security/keys", "/api/security/keys:rotate").contains(path));
    }

    @DynamicPropertySource
    static void keyProperties(DynamicPropertyRegistry properties) {
        String material = java.util.UUID.randomUUID().toString();
        properties.add("summer.sample.security.master-key", () -> material);
    }

    @Autowired MockMvc mvc;
    @Autowired AuditEvents audit;
    @Autowired JdbcTemplate jdbc;

    @Test
    void rejectedRotationsKeepTheCurrentVersionAndPublishOneFailureAuditEach() throws Exception {
        for (String[] example : new String[][]{
                {"0", "1", "400", "SUMMER_KEY_ROTATION_INVALID", "retentionDays must be between 1 and 365"},
                {"30", "999", "412", "SUMMER_PRECONDITION_FAILED", "The signing key version has changed"},
                {"30", "invalid", "400", "SUMMER_KEY_ROTATION_INVALID", "If-Match must contain a key version"}}) {
            mvc.perform(post("/api/security/keys:rotate")
                            .contentType("application/json").content("{\"retentionDays\":" + example[0] + "}")
                            .header("If-Match", example[1]).header("X-Request-Id", "rotation-error")
                            .with(jwt().jwt(keyClaims -> keyClaims.claim("scope", "security.key.read security.key.rotate")).authorities(new SimpleGrantedAuthority("SCOPE_security.key.rotate"))))
                    .andExpect(status().is(Integer.parseInt(example[2])))
                    .andExpect(jsonPath("$.error.code").value(example[3]))
                    .andExpect(jsonPath("$.error.message").value(example[4]))
                    .andExpect(jsonPath("$.requestId").value("rotation-error"))
                    .andExpect(jsonPath("$.error.cause").doesNotExist())
                    .andExpect(header().string("X-Request-Id", "rotation-error"));
        }
        assertThat(audit.results).containsExactly("SUMMER_KEY_ROTATION_INVALID", "SUMMER_PRECONDITION_FAILED", "SUMMER_KEY_ROTATION_INVALID");
        assertCurrentVersion();
    }

    @Test
    void aStorageFailureKeepsTheSafeMessageAndAllowsTheOriginalKeyAfterRecovery() throws Exception {
        jdbc.execute("ALTER TABLE summer_security_keys RENAME TO ticket11_keys_fault");
        try {
            mvc.perform(post("/api/security/keys:rotate").contentType("application/json")
                            .content("{\"retentionDays\":30}").header("If-Match", "1")
                            .with(jwt().jwt(keyClaims -> keyClaims.claim("scope", "security.key.read security.key.rotate")).authorities(new SimpleGrantedAuthority("SCOPE_security.key.rotate"))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error.code").value("SUMMER_INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.error.message").value("Key rotation failed"))
                    .andExpect(jsonPath("$.error.cause").doesNotExist());
            assertThat(audit.results).containsExactly("FAILED");
        } finally {
            jdbc.execute("ALTER TABLE ticket11_keys_fault RENAME TO summer_security_keys");
        }
        assertCurrentVersion();
    }

    private void assertCurrentVersion() throws Exception {
        mvc.perform(get("/api/security/keys").with(jwt().jwt(keyClaims -> keyClaims.claim("scope", "security.key.read security.key.rotate")).authorities(new SimpleGrantedAuthority("SCOPE_security.key.read"))))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"));
    }

    static class AuditEvents implements SecurityAuditLogger {
        final List<String> results = new CopyOnWriteArrayList<>();
        @Override public void record(String event, String subject, Map<String, ?> attributes) {
            if ("security_key_rotation_failed".equals(event)) results.add(String.valueOf(attributes.get("result")));
        }
    }

    @TestConfiguration
    static class AuditConfiguration {
        @Bean @Primary AuditEvents rotationAuditSink() { return new AuditEvents(); }
    }
}
