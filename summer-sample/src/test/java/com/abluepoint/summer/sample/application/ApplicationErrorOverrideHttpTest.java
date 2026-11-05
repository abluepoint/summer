package com.abluepoint.summer.sample.application;

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
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ApplicationErrorOverrideHttpTest.StatusOverride.class)
class ApplicationErrorOverrideHttpTest {
    @Autowired MockMvc mvc;

    @Test void applicationValidationUsesTheSharedStatusPolicyAndKeepsItsLiteralMessage() throws Exception {
        mvc.perform(post("/api/v1/enterprises/ent-acme/applications")
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("Idempotency-Key", "application-policy")
                        .header("X-Request-Id", "application-policy")
                        .with(jwt().jwt(token -> token.claim("scope", "enterprise.application.write").subject("admin")
                                        .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                                        .claim("enterprise_capabilities", Map.of("ent-acme", List.of("enterprise.application.write"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_enterprise.application.write"))))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("SUMMER_APPLICATION_INVALID"))
                .andExpect(jsonPath("$.error.message").value("name is required"))
                .andExpect(jsonPath("$.requestId").value("application-policy"))
                .andExpect(header().string("X-Request-Id", "application-policy"));
    }

    @TestConfiguration
    static class StatusOverride {
        @Bean ErrorStatusResolver applicationStatusPolicy() {
            var defaults = new DefaultErrorStatusResolver();
            return code -> "SUMMER_APPLICATION_INVALID".equals(code.code())
                    ? HttpStatus.UNPROCESSABLE_CONTENT : defaults.resolve(code);
        }
    }
}
