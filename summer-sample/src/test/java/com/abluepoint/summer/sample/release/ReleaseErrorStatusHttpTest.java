package com.abluepoint.summer.sample.release;


import com.abluepoint.summer.common.error.api.ErrorCategory;
import com.abluepoint.summer.mvc.error.spi.ErrorStatusResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ReleaseErrorStatusHttpTest.TransportPolicy.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReleaseErrorStatusHttpTest {
    @Autowired MockMvc mvc;

    @Test void applicationCanOverrideReleaseValidationStatusWithoutChangingItsBusinessCode() throws Exception {
        mvc.perform(post("/internal/v1/release/gates:validate")
                        .with(jwt().jwt(token -> token.subject("release-operator").claim("release_operator", true))
                                .authorities(new SimpleGrantedAuthority("SCOPE_internal.release.write")))
                        .header("Idempotency-Key", "typed-validation").header("X-Request-Id", "typed-release-error")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"releaseVersion\":\"candidate\",\"dryRun\":false}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("IAM_RELEASE_INVALID"))
                .andExpect(jsonPath("$.requestId").value("typed-release-error"));
    }

    @TestConfiguration
    static class TransportPolicy {
        @Bean @Primary ErrorStatusResolver releaseTransportPolicy() {
            return code -> switch (code.category()) {
                case INVALID_ARGUMENT -> HttpStatus.UNPROCESSABLE_CONTENT;
                case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
                case FORBIDDEN -> HttpStatus.FORBIDDEN;
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case CONFLICT -> HttpStatus.CONFLICT;
                case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
        }
    }
}
