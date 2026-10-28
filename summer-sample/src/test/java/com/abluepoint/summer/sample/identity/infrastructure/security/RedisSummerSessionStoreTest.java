package com.abluepoint.summer.sample.identity.infrastructure.security;

import com.abluepoint.summer.sample.identity.application.SessionIndexService;
import com.abluepoint.summer.sample.identity.domain.SessionIndexUnavailableException;
import com.abluepoint.summer.sample.identity.domain.SessionRecord;

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
        when(index.touch(anyString(), anyInt())).thenThrow(new SessionIndexUnavailableException());
        assertThatThrownBy(() -> store.loadDeferredContext(request).get()).isInstanceOf(SessionIndexUnavailableException.class);
        when(index.load(anyString())).thenThrow(new SessionIndexUnavailableException());
        assertThat(store.containsContext(request)).isFalse();
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
        when(index.ensureCurrent(any(), any())).thenThrow(new SessionIndexUnavailableException());
        assertThatThrownBy(() -> store.saveContext(context, request, new MockHttpServletResponse()))
                .isInstanceOf(SessionIndexUnavailableException.class);
        assertThat(request.getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
    }

    @Test void savingEmptyContextRevokesTheSharedRecordAndClearsLocalAuthentication() {
        var request = localAuthenticatedRequest();
        store.saveContext(SecurityContextHolder.createEmptyContext(), request, new MockHttpServletResponse());
        verify(index).revokeCurrent(request, null);
        assertThat(request.getSession().getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
    }

    private SessionRecord record(String status, Instant expiry) {
        return new SessionRecord("reference", "shared-user", "summer-ui", status, Instant.now().minusSeconds(10),
                Instant.now(), expiry, Map.of(), List.of("ROLE_USER"));
    }
}
