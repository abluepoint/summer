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
class RuntimeDiagnosticsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void rejectsRequestsWithoutTheDiagnosticsCapability() throws Exception {
        mockMvc.perform(get("/internal/v1/diagnostics/runtime")
                        .with(jwt().jwt(token -> token.subject("viewer"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"));
    }

    @Test
    void returnsHealthMetersAndRequestCorrelationWithoutSecrets() throws Exception {
        mockMvc.perform(get("/internal/v1/diagnostics/runtime")
                        .header("X-Request-Id", "runtime-diagnostic-request")
                        .with(jwt().jwt(token -> token.subject("operator"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_platform.capabilities.read"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "runtime-diagnostic-request"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").value("runtime-diagnostic-request"))
                .andExpect(jsonPath("$.data.components").isArray())
                .andExpect(jsonPath("$.data.metrics.registeredMeters").isNumber())
                .andExpect(jsonPath("$.data.metrics.meterNames").isArray())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.privateKey").doesNotExist());
    }
}
