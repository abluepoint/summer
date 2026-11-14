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
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class ResilienceDiagnosticsHttpTest {
    @Autowired MockMvc http;
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    String subject;

    @org.junit.jupiter.api.BeforeEach
    void grantExactDiagnostics() {
        subject = "resilience-" + java.util.UUID.randomUUID();
        current.grantInstalled(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), subject,
                mapping -> mapping.source().equals("MONITORING")
                        && mapping.path().equals("/internal/v1/diagnostics/resilience")
                        && java.util.Set.of("GET", "HEAD").contains(mapping.method()));
    }

    @Test
    void exposes_resilience_state_and_metrics_without_secret_values() throws Exception {
        http.perform(get("/internal/v1/diagnostics/resilience")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(token -> token.subject(subject).claim("scope", "platform.capabilities.read"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.capabilities.read"))))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                        .as("Current diagnostics positive; resolved exception: %s", result.getResolvedException())
                        .isEqualTo(200))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.operations").isArray())
                .andExpect(jsonPath("$.data.operations[0].name").isString())
                .andExpect(jsonPath("$.data.operations[0].circuitBreaker").isString())
                .andExpect(jsonPath("$.data.operations[0].calls").isNumber())
                .andExpect(jsonPath("$.data.operations[0].retryAttempts").isNumber());
    }

    @Test
    void authorityWithoutRawDelegationCannotReadDiagnostics() throws Exception {
        http.perform(get("/internal/v1/diagnostics/resilience")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(token -> token.subject(subject).claim("scope", "customer.read"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.capabilities.read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejects_resilience_diagnostics_without_capability() throws Exception {
        http.perform(get("/internal/v1/diagnostics/resilience")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_customer.read"))))
                .andExpect(status().isForbidden());
    }
}
