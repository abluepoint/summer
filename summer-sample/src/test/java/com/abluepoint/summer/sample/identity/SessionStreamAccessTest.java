package com.abluepoint.summer.sample.identity;

import com.abluepoint.summer.security.autoconfigure.SummerSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionStreamAccessTest {
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final org.springframework.security.core.Authentication authentication =
            new UsernamePasswordAuthenticationToken("reader", null, List.of());

    @Test
    void capturedCookieSessionConsultsCurrentSharedRecordAndFailsAfterRevocation() {
        var index = mock(SessionIndexService.class);
        String id = request.getSession().getId();
        when(index.load(id)).thenReturn(record("reader", "ACTIVE", Instant.now().plusSeconds(300)));
        var lease = access("redis", index).capture(request, authentication);
        assertThat(lease.getAsBoolean()).isTrue();
        for (var value : List.of(record("reader", "REVOKED", Instant.now().plusSeconds(300)),
                record("reader", "ACTIVE", Instant.now().minusSeconds(1)),
                record("foreign", "ACTIVE", Instant.now().plusSeconds(300)))) {
            when(index.load(id)).thenReturn(value);
            assertThat(lease.getAsBoolean()).isFalse();
        }
        when(index.load(id)).thenReturn(null);
        assertThat(lease.getAsBoolean()).isFalse();
    }

    @Test
    void sharedBackendFailureCannotBecomeAnAllowDecision() {
        var index = mock(SessionIndexService.class);
        String id = request.getSession().getId();
        when(index.load(id)).thenThrow(new SessionIndexUnavailableException());
        var lease = access("redis", index).capture(request, authentication);
        assertThatThrownBy(lease::getAsBoolean).isInstanceOf(SessionIndexUnavailableException.class);
    }

    @Test
    void bearerCredentialsRemainIndependentOfAnAccompanyingCookie() {
        var index = mock(SessionIndexService.class);
        request.getSession();
        request.addHeader("Authorization", "Bearer test-fixture");
        assertThat(access("redis", index).capture(request, authentication).getAsBoolean()).isTrue();
        verifyNoInteractions(index);
    }

    @Test
    void localSessionInvalidationEndsItsLeaseEvenWhenAnIndexBeanExists() {
        var index = mock(SessionIndexService.class);
        var session = request.getSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        var lease = access("memory", index).capture(request, authentication);
        assertThat(lease.getAsBoolean()).isTrue();
        session.invalidate();
        assertThat(lease.getAsBoolean()).isFalse();
        verifyNoInteractions(index);
    }

    @Test
    void absentCookieOrSharedBackendFailsClosed() {
        assertThat(access("redis", null).capture(request, authentication).getAsBoolean()).isFalse();
        request.getSession();
        assertThat(access("redis", null).capture(request, authentication).getAsBoolean()).isFalse();
    }

    private SessionStreamAccess access(String mode, SessionIndexService index) {
        var factory = new StaticListableBeanFactory();
        if (index != null) factory.addBean("index", index);
        var properties = new SummerSecurityProperties();
        properties.getStorage().setSession(mode);
        return new SessionStreamAccess(factory.getBeanProvider(SessionIndexService.class), properties);
    }

    private SessionRecord record(String principal, String status, Instant expires) {
        return new SessionRecord("fixture", principal, "summer-ui", status, Instant.now(), Instant.now(),
                expires, Map.of(), List.of());
    }
}
