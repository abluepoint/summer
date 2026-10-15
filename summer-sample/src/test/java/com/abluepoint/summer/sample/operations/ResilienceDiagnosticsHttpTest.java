package com.abluepoint.summer.sample.operations;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResilienceDiagnosticsHttpTest {
    @Autowired MockMvc http;

    @Test
    void exposes_resilience_state_and_metrics_without_secret_values() throws Exception {
        http.perform(get("/internal/v1/diagnostics/resilience")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(token -> token.claim("active_enterprise_id", "ent-acme")
                                        .claim("enterprise_memberships", java.util.List.of(java.util.Map.of(
                                                "enterpriseId", "ent-acme", "status", "ACTIVE"))))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.capabilities.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.operations").isArray())
                .andExpect(jsonPath("$.data.operations[0].name").isString())
                .andExpect(jsonPath("$.data.operations[0].circuitBreaker").isString())
                .andExpect(jsonPath("$.data.operations[0].calls").isNumber())
                .andExpect(jsonPath("$.data.operations[0].retryAttempts").isNumber());
    }

    @Test
    void rejects_resilience_diagnostics_without_capability() throws Exception {
        http.perform(get("/internal/v1/diagnostics/resilience")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_customer.read"))))
                .andExpect(status().isForbidden());
    }
}
