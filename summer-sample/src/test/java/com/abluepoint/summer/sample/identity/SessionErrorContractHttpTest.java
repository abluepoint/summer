package com.abluepoint.summer.sample.identity;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:session-error-contract;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "summer.security.storage.session=memory", "summer.sample.session-index.enabled=true",
        "summer.sample.session-index.retries=2", "summer.sample.session-index.failure-threshold=100",
        "summer.sample.session-index.timeout-ms=1000"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.context.annotation.Import(com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture.class)
class SessionErrorContractHttpTest {
    @Autowired com.abluepoint.summer.sample.authorization.CurrentManagedAuthorizationTestFixture current;
    @org.junit.jupiter.api.BeforeEach
    void grantExactCurrentOperations() {
        for (String subject : java.util.List.of("reader", "user"))
            current.grantHandlers(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), subject,
                    (handler, path, method) -> handler.getBeanType().getName().equals("com.abluepoint.summer.sample.identity.SessionController")
                            && java.util.Set.of("/api/v1/sessions", "/api/v1/sessions/{reference}").contains(path));
    }

    private static final String REFERENCE = "a".repeat(64);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean StringRedisTemplate redis;
    ValueOperations<String, String> values;
    SetOperations<String, String> sets;

    @BeforeEach @SuppressWarnings("unchecked") void externalRedis() {
        values = mock(ValueOperations.class);
        sets = mock(SetOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForSet()).thenReturn(sets);
    }

    @Test void malformedReferenceIs400BeforeTheUnavailableBackendIsConsulted() throws Exception {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("private backend detail"));
        error(delete("/api/v1/sessions/not-a-reference"), 400, "SUMMER_SESSION_REFERENCE_INVALID",
                "The session reference must be a 64-character hexadecimal value");
    }

    @Test void missingSessionIs404WithoutRetryingTheBusinessFailure() throws Exception {
        // A retry would turn the missing business result into a technical 503.
        when(values.get(anyString())).thenReturn(null).thenThrow(new RedisConnectionFailureException("unexpected business retry"));
        error(delete("/api/v1/sessions/" + REFERENCE), 404, "SUMMER_SESSION_NOT_FOUND", "The session was not found");
    }

    @Test void anotherPrincipalsSessionKeepsTheNative403Message() throws Exception {
        when(values.get(anyString())).thenReturn(record("another-user", "ACTIVE", Instant.now().plusSeconds(300)))
                .thenThrow(new RedisConnectionFailureException("unexpected business retry"));
        error(delete("/api/v1/sessions/" + REFERENCE), 403, "SUMMER_FORBIDDEN", "Session belongs to another principal");
    }

    @Test void revokedAndExpiredCookieSessionsAre401WithoutTechnicalRetry() throws Exception {
        for (String json : List.of(record("reader", "REVOKED", Instant.now().plusSeconds(300)),
                record("reader", "ACTIVE", Instant.now().minusSeconds(1)))) {
            doReturn(json).doThrow(new RedisConnectionFailureException("unexpected business retry")).when(values).get(anyString());
            error(get("/api/v1/session").session(new MockHttpSession()), 401, "SUMMER_SESSION_REVOKED", "The server session has been revoked");
        }
    }

    @Test void snapshotListAndRevokeFailuresKeep503JsonAndTheOriginalTechnicalCause() throws Exception {
        var cause = new RedisConnectionFailureException("private backend detail");
        when(values.get(anyString())).thenThrow(cause);
        when(sets.members(anyString())).thenThrow(cause);
        for (var request : List.of(get("/api/v1/session").session(new MockHttpSession()),
                get("/api/v1/sessions"), delete("/api/v1/sessions/" + REFERENCE))) {
            error(request, 503, "SUMMER_SESSION_STORE_UNAVAILABLE", "Shared session storage is unavailable")
                    .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOfSatisfying(SummerException.class,
                            failure -> {
                                assertThat(failure.errorCode()).isSameAs(SampleTransportErrorCode.SESSION_STORE_UNAVAILABLE);
                                assertThat(failure.getCause()).isSameAs(cause);
                            }));
        }
    }

    @Test void logoutFailureRemainsAnEmpty503AndDoesNotDestroyTheCookieSession() throws Exception {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("private backend detail"));
        var session = new MockHttpSession();
        mvc.perform(delete("/api/v1/session").session(session).with(jwt()).header("X-Request-Id", "logout-failure"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("X-Request-Id", "logout-failure"))
                .andExpect(content().string(""));
        assertThat(session.isInvalid()).isFalse();
    }

    @Test void aTransientTechnicalFailureCanRecoverWithinTheExistingRetryBudget() throws Exception {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("transient"))
                .thenThrow(new RedisConnectionFailureException("transient")) .thenReturn(null);
        mvc.perform(get("/api/v1/session").session(new MockHttpSession()).with(jwt().jwt(token -> token.subject("reader").claim("scope", "platform.capabilities.read platform.capabilities.write"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.principal.subject").value("reader"));
    }

    @Test void listPermissionFailureKeepsItsLiteralNativeMessage() throws Exception {
        mvc.perform(get("/api/v1/sessions").with(jwt()).header("X-Request-Id", "session-no-capability"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.message").value("Session read capability denied"));
    }

    private ResultActions error(MockHttpServletRequestBuilder request, int expectedStatus, String code, String message) throws Exception {
        return mvc.perform(request.with(jwt().jwt(token -> token.subject("reader").claim("scope", "platform.capabilities.read platform.capabilities.write"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_platform.capabilities.read"),
                                        new SimpleGrantedAuthority("SCOPE_platform.capabilities.write")))
                        .header("X-Request-Id", "session-contract").header("Accept-Language", "zh-CN"))
                .andExpect(status().is(expectedStatus)).andExpect(header().string("X-Request-Id", "session-contract"))
                .andExpect(jsonPath("$.error.code").value(code)).andExpect(jsonPath("$.error.message").value(message))
                .andExpect(jsonPath("$.error.violations").isEmpty()).andExpect(jsonPath("$.requestId").value("session-contract"));
    }
    private String record(String principal, String status, Instant expiresAt) {
        return mapper.writeValueAsString(new SessionRecord(REFERENCE, principal, "summer-ui", status,
                Instant.now().minusSeconds(10), Instant.now(), expiresAt, Map.of(), List.of()));
    }
}
