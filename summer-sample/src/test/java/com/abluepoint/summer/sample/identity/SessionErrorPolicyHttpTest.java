package com.abluepoint.summer.sample.identity;

import com.abluepoint.summer.mvc.error.DefaultErrorStatusResolver;
import com.abluepoint.summer.mvc.error.ErrorStatusResolver;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
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
        "spring.datasource.url=jdbc:h2:mem:session-error-policy;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "summer.security.storage.session=memory",
        "summer.sample.session-index.enabled=true",
        "summer.sample.session-index.retries=0"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(SessionErrorPolicyHttpTest.TransportPolicy.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SessionErrorPolicyHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean StringRedisTemplate redis;

    @BeforeEach @SuppressWarnings("unchecked") void unavailableRedis() {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("private backend detail"));
    }

    @Test void sessionFailureUsesTheApplicationResolverAndKeepsItsPublicMessage() throws Exception {
        mvc.perform(get("/api/v1/session").session(new MockHttpSession())
                        .with(jwt().jwt(token -> token.subject("reader")))
                        .header("X-Request-Id", "session-policy-request"))
                .andExpect(status().is(422))
                .andExpect(header().string("X-Request-Id", "session-policy-request"))
                .andExpect(jsonPath("$.error.code").value("SUMMER_SESSION_STORE_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").value("Shared session storage is unavailable"))
                .andExpect(jsonPath("$.requestId").value("session-policy-request"));
    }

    @Test void nativeBusinessDenialUsesTheApplicationResolverWithoutLosingItsCause() throws Exception {
        mvc.perform(get("/api/v1/sessions").with(jwt()).header("X-Request-Id", "native-session-denial"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error.code").value("SUMMER_FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Session read capability denied"))
                .andExpect(header().string("X-Request-Id", "native-session-denial"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResolvedException())
                        .isInstanceOfSatisfying(com.abluepoint.summer.common.error.SummerException.class,
                                failure -> org.assertj.core.api.Assertions.assertThat(failure.getCause())
                                        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TransportPolicy {
        @Bean @Primary ErrorStatusResolver applicationStatusResolver() {
            var defaults = new DefaultErrorStatusResolver();
            return code -> code == SampleTransportErrorCode.SESSION_STORE_UNAVAILABLE
                    || code == com.abluepoint.summer.common.error.CommonErrorCode.FORBIDDEN
                    ? HttpStatus.valueOf(422) : defaults.resolve(code);
        }
    }
}
