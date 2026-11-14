package com.abluepoint.summer.sample.key;

import com.abluepoint.summer.mvc.error.DefaultErrorStatusResolver;
import com.abluepoint.summer.mvc.error.ErrorStatusResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "summer.security.storage.keys=mysql")
@AutoConfigureMockMvc
@Import({KeyRotationErrorOverrideHttpTest.StatusOverride.class, com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class})
class KeyRotationErrorOverrideHttpTest {
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

    @Test
    void rotationValidationUsesApplicationPolicyWithoutChangingItsPublicMessage() throws Exception {
        mvc.perform(post("/api/security/keys:rotate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retentionDays\":0}").header("If-Match", "\"1\"")
                        .header("X-Request-Id", "key-policy")
                        .with(jwt().jwt(keyClaims -> keyClaims.claim("scope", "security.key.read security.key.rotate")).authorities(new SimpleGrantedAuthority("SCOPE_security.key.rotate"))))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("SUMMER_KEY_ROTATION_INVALID"))
                .andExpect(jsonPath("$.error.message").value("retentionDays must be between 1 and 365"))
                .andExpect(jsonPath("$.requestId").value("key-policy"))
                .andExpect(header().string("X-Request-Id", "key-policy"));
    }

    @TestConfiguration
    static class StatusOverride {
        @Bean ErrorStatusResolver keyStatusPolicy() {
            var defaults = new DefaultErrorStatusResolver();
            return code -> "SUMMER_KEY_ROTATION_INVALID".equals(code.code())
                    ? HttpStatus.UNPROCESSABLE_CONTENT : defaults.resolve(code);
        }
    }
}
