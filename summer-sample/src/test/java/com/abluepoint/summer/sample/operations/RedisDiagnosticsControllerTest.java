package com.abluepoint.summer.sample.operations;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class RedisDiagnosticsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/internal/v1/diagnostics/redis"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void requiresTheExistingPlatformDiagnosticsCapability() throws Exception {
        mockMvc.perform(get("/internal/v1/diagnostics/redis")
                        .with(jwt().jwt(token -> token.subject("viewer"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void returnsARequestCorrelatedSanitizedHealthDocument() throws Exception {
        mockMvc.perform(get("/internal/v1/diagnostics/redis")
                        .header("X-Request-Id", "redis-diagnostic-request")
                        .with(jwt().jwt(token -> token.subject("operator"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_platform.capabilities.read"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "redis-diagnostic-request"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.component").value("REDIS"))
                .andExpect(jsonPath("$.data.status").isString())
                .andExpect(jsonPath("$.data.endpoint").value("redis"))
                .andExpect(jsonPath("$.data.checkedAt").isString())
                .andExpect(jsonPath("$.data.connectionUri").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.metrics.password").doesNotExist());
    }
}
