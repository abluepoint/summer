package com.abluepoint.summer.sample.key;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest(properties = {
        "summer.security.storage.keys=mysql",
        "summer.sample.security.master-key=test-only-runtime-master-key-32-bytes",
        "summer.sample.security.key-rotation.period-days=90",
        "summer.sample.security.key-rotation.retention-days=30",
        "summer.sample.session-index.enabled=true"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AutomaticKeyRotationHttpTest {
    @Autowired MockMvc mockMvc;

    @Test
    void exposesAutomaticRotationScheduleWithoutKeyMaterial() throws Exception {
        mockMvc.perform(get("/api/security/keys").with(jwt().authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_security.key.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rotation.periodDays").value(90))
                .andExpect(jsonPath("$.data.rotation.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.rotation.nextRotationAt").isString())
                .andExpect(jsonPath("$.data.rotation.failureCount").value(0))
                .andExpect(jsonPath("$.data.rotation.privateKey").doesNotExist());
    }
}
