package com.abluepoint.summer.sample.systemparameter;

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

@SpringBootTest(properties="summer.sample.parameter-events.scheduling-enabled=false")
@AutoConfigureMockMvc
@Import(SystemParameterErrorPolicyHttpTest.Policy.class)
class SystemParameterErrorPolicyHttpTest {
    @Autowired MockMvc mvc;

    @Test
    void fieldFailureUsesApplicationStatusPolicyAndPreservesBusinessDetails() throws Exception {
        mvc.perform(post("/api/system-parameters").header("X-Request-Id", "parameter-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"policy.limit\",\"type\":\"INTEGER\",\"value\":101,\"max\":100}")
                        .with(jwt().jwt(token -> token.claim("scope", "system.parameter.write").subject("operator").claim("enterprise_memberships",
                                        List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_system.parameter.write"))))
                .andExpect(status().is(422))
                .andExpect(header().string("X-Request-Id", "parameter-policy"))
                .andExpect(jsonPath("$.requestId").value("parameter-policy"))
                .andExpect(jsonPath("$.error.code").value("SUMMER_PARAMETER_INVALID"))
                .andExpect(jsonPath("$.error.message").value("System parameter validation failed"))
                .andExpect(jsonPath("$.error.violations.length()").value(1))
                .andExpect(jsonPath("$.error.violations[0].field").value("value"))
                .andExpect(jsonPath("$.error.violations[0].message").value("is above max"))
                .andExpect(jsonPath("$.error.cause").doesNotExist());
    }

    @TestConfiguration
    static class Policy {
        @Bean ErrorStatusResolver parameterStatusPolicy() {
            var defaults = new DefaultErrorStatusResolver();
            return code -> "SUMMER_PARAMETER_INVALID".equals(code.code())
                    ? HttpStatus.UNPROCESSABLE_CONTENT : defaults.resolve(code);
        }
    }
}
