package com.abluepoint.summer.sample.identity;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisSummerSessionStoreTest {
    private final SessionIndexService index = mock(SessionIndexService.class);
    private final RedisSummerSessionStore store = new RedisSummerSessionStore(index);

    private MockHttpServletRequest localAuthenticatedRequest() {
        var request = new MockHttpServletRequest();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("test-user", null, List.of()));
        request.getSession().setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return request;
    }

    @Test void missingSharedRecordCannotUseLocalAuthentication() {
        var request = localAuthenticatedRequest();
        assertThat(store.loadDeferredContext(request).get().getAuthentication()).isNull();
        assertThat(store.containsContext(request)).isFalse();
    }

    @Test void redisOutageCannotUseLocalAuthentication() {
        var request = localAuthenticatedRequest();
        when(index.touch(anyString(), anyInt())).thenThrow(SummerException.withMessage(SampleTransportErrorCode.SESSION_STORE_UNAVAILABLE, "Shared session storage is unavailable"));
        assertThatThrownBy(() -> store.loadDeferredContext(request).get()).isInstanceOfSatisfying(SummerException.class, failure -> assertThat(failure.errorCode()).isSameAs(SampleTransportErrorCode.SESSION_STORE_UNAVAILABLE));
        when(index.load(anyString())).thenThrow(SummerException.withMessage(SampleTransportErrorCode.SESSION_STORE_UNAVAILABLE, "Shared session storage is unavailable"));
        assertThat(store.containsContext(request)).isFalse();
    }

    @Test void unrelatedBusinessFailureIsNotSilentlyClassifiedAsAStorageOutage() {
        var request = localAuthenticatedRequest();
        var failure = SummerException.withMessage(IdentityErrorCode.USER_CONFLICT, "Username already exists");
        when(index.load(anyString())).thenThrow(failure);
        assertThatThrownBy(() -> store.containsContext(request)).isSameAs(failure);
    }

    @Test void revokedOrExpiredSharedRecordCannotUseLocalAuthentication() {
        var request = localAuthenticatedRequest();
        for (var record : List.of(record("REVOKED", Instant.now().plusSeconds(60)), record("ACTIVE", Instant.now().minusSeconds(1)))) {
            when(index.load(anyString())).thenReturn(record);
            when(index.touch(anyString(), anyInt())).thenReturn(record);
            assertThat(store.loadDeferredContext(request).get().getAuthentication()).isNull();
            assertThat(store.containsContext(request)).isFalse();
        }
    }

    @Test void activeSharedRecordAllowsTheCompleteStoredAuthentication() {
        var request = localAuthenticatedRequest();
        var active = record("ACTIVE", Instant.now().plusSeconds(60));
        when(index.load(request.getSession().getId())).thenReturn(active);
        when(index.touch(anyString(), anyInt())).thenReturn(active);
        assertThat(store.loadDeferredContext(request).get().getAuthentication().getName()).isEqualTo("test-user");
        assertThat(store.containsContext(request)).isTrue();
    }

    @Test void failedSharedSaveDoesNotLeaveLocalAuthentication() {
        var request = localAuthenticatedRequest();
        var context = (org.springframework.security.core.context.SecurityContext) request.getSession()
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        when(index.ensureCurrent(any(), any())).thenThrow(SummerException.withMessage(SampleTransportErrorCode.SESSION_STORE_UNAVAILABLE, "Shared session storage is unavailable"));
        assertThatThrownBy(() -> store.saveContext(context, request, new MockHttpServletResponse()))
                .isInstanceOfSatisfying(SummerException.class, failure -> assertThat(failure.errorCode()).isSameAs(SampleTransportErrorCode.SESSION_STORE_UNAVAILABLE));
        assertThat(request.getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
    }

    @Test void savingEmptyContextRevokesTheSharedRecordAndClearsLocalAuthentication() {
        var request = localAuthenticatedRequest();
        store.saveContext(SecurityContextHolder.createEmptyContext(), request, new MockHttpServletResponse());
        verify(index).revokeCurrent(request, null);
        assertThat(request.getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
    }

    @Test @SuppressWarnings("unchecked") void revokedBusinessCallbackIsNotRetriedAsAStorageFailure() {
        var request = localAuthenticatedRequest();
        var context = (org.springframework.security.core.context.SecurityContext) request.getSession()
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        var redis = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, String> values = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(mapper.writeValueAsString(record("REVOKED", Instant.now().plusSeconds(300))))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("unexpected business retry"));
        var realIndex = new SessionIndexService(redis, mapper, new SessionIndexProperties());
        try {
            var repository = new RedisSummerSessionStore(realIndex);
            assertThatThrownBy(() -> repository.saveContext(context, request, new MockHttpServletResponse()))
                    .isInstanceOfSatisfying(SummerException.class, failure ->
                            assertThat(failure.errorCode()).isSameAs(IdentityErrorCode.SESSION_REVOKED));
            assertThat(request.getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        } finally { realIndex.close(); }
    }

    private SessionRecord record(String status, Instant expiry) {
        return new SessionRecord("reference", "shared-user", "summer-ui", status, Instant.now().minusSeconds(10),
                Instant.now(), expiry, Map.of(), List.of("ROLE_USER"));
    }
}
