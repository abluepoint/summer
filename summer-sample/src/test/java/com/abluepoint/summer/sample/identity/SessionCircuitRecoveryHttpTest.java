package com.abluepoint.summer.sample.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:session-circuit-recovery;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "summer.security.storage.session=memory", "summer.sample.session-index.enabled=true",
        "summer.sample.session-index.retries=0", "summer.sample.session-index.failure-threshold=1",
        "summer.sample.session-index.open-duration-ms=1000", "summer.sample.session-index.timeout-ms=1000"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SessionCircuitRecoveryHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean StringRedisTemplate redis;

    @Test @SuppressWarnings("unchecked")
    void outageOpensTheCircuitAndARecoveredBackendBecomesUsableAfterTheExistingOpenDuration() throws Exception {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("transient outage")).thenReturn(null);
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(get("/api/v1/session").session(new MockHttpSession()).with(jwt()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value("SUMMER_SESSION_STORE_UNAVAILABLE"));
        }
        Thread.sleep(1100);
        mvc.perform(get("/api/v1/session").session(new MockHttpSession()).with(jwt()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    }
}
