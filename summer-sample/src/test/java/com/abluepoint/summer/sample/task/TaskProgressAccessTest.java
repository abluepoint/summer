package com.abluepoint.summer.sample.task;
import com.abluepoint.summer.sample.task.infrastructure.TaskProgressAccess;

import com.abluepoint.summer.sample.authorization.application.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TaskProgressAccessTest {
    @Test
    void missingOrExpiredTokenLifetimeFailsBeforeReadingPersistedGrants() {
        var authorization = mock(AuthorizationService.class);
        var access = new TaskProgressAccess(authorization);
        for (Instant expiry : new Instant[] { null, Instant.now().minusSeconds(60) }) {
            var token = Jwt.withTokenValue("test").header("alg", "none").subject("reader")
                    .issuedAt(Instant.now().minusSeconds(3600));
            if (expiry != null) token.expiresAt(expiry);
            var authentication = new JwtAuthenticationToken(token.build(), List.of(new SimpleGrantedAuthority("SCOPE_enterprise.tasks.read")));
            assertThatThrownBy(() -> access.requireReadable("ent-acme", authentication)).isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(authorization);
    }

    @Test
    void currentPersistentDenyStopsAStillValidToken() {
        var authorization = mock(AuthorizationService.class);
        var access = new TaskProgressAccess(authorization);
        var token = Jwt.withTokenValue("test").header("alg", "none").subject("reader")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        var authentication = new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("SCOPE_enterprise.tasks.read")));
        when(authorization.decision(authentication, "ent-acme", "GET:/api/v1/enterprises/{enterpriseId}/tasks/{taskId}/events", null))
                .thenReturn(new AuthorizationService.Decision(false, "USER_EXPLICIT_DENY"));
        assertThatThrownBy(() -> access.requireReadable("ent-acme", authentication)).isInstanceOf(AccessDeniedException.class);
        verify(authorization).requireEnterpriseMembership("ent-acme", authentication);
    }
}
