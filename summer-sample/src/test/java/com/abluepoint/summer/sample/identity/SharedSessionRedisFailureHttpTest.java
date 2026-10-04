package com.abluepoint.summer.sample.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "summer.sample.session-index.enabled=true",
        "summer.security.storage.session=memory",
        "spring.session.store-type=none",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "spring.data.redis.connect-timeout=20ms",
        "spring.data.redis.timeout=20ms",
        "summer.sample.session-index.timeout-ms=50ms",
        "summer.sample.session-index.retries=0",
        "summer.sample.session-index.failure-threshold=1",
        "summer.sample.session-index.open-duration-ms=1s"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class SharedSessionRedisFailureHttpTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void exposesAStableDiagnosticWhenRedisIsUnavailable() throws Exception {
        var operator = SecurityMockMvcRequestPostProcessors.jwt().jwt(jwt -> jwt.subject("operator"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_platform.capabilities.read"));

        mockMvc.perform(get("/api/v1/sessions").with(operator))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("SUMMER_SESSION_STORE_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").isString());
    }
}
