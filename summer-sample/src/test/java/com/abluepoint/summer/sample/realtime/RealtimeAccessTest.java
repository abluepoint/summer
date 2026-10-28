package com.abluepoint.summer.sample.realtime;
import com.abluepoint.summer.sample.realtime.infrastructure.RealtimeAccess;
import com.abluepoint.summer.sample.realtime.application.RealtimeMessageService;

import com.abluepoint.summer.sample.authorization.application.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RealtimeAccessTest {
    @Test void missingOrExpiredLifetimeCannotUseSignedGrants() {
        var authorization = mock(AuthorizationService.class);
        var access = new RealtimeAccess(authorization);
        for (Instant expiry : new Instant[] {null, Instant.now().minusSeconds(30)}) {
            assertThatThrownBy(() -> access.require("ent-acme", token(expiry, "ent-acme"), RealtimeMessageService.READ))
                    .isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(authorization);
    }

    @Test void anotherTenantsClaimCannotSupplyTheCapability() {
        var authorization = mock(AuthorizationService.class);
        assertThatThrownBy(() -> new RealtimeAccess(authorization).require("ent-acme",
                token(Instant.now().plusSeconds(300), "ent-other"), RealtimeMessageService.READ))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(authorization);
    }

    @Test void revokedPersistedRoleStopsAStillValidToken() {
        var authorization = mock(AuthorizationService.class);
        var authentication = token(Instant.now().plusSeconds(300), "ent-acme");
        when(authorization.decision(authentication, "ent-acme", RealtimeMessageService.READ, null))
                .thenReturn(new AuthorizationService.Decision(false, "USER_EXPLICIT_DENY"));
        assertThatThrownBy(() -> new RealtimeAccess(authorization).require("ent-acme", authentication, RealtimeMessageService.READ))
                .isInstanceOf(AccessDeniedException.class);
        verify(authorization).requireEnterpriseMembership("ent-acme", authentication);
    }

    @Test void inactivePersistedMembershipStopsAStillValidToken() {
        var authorization = mock(AuthorizationService.class);
        var authentication = token(Instant.now().plusSeconds(300), "ent-acme");
        doThrow(new AccessDeniedException("membership inactive")).when(authorization)
                .requireEnterpriseMembership("ent-acme", authentication);
        assertThatThrownBy(() -> new RealtimeAccess(authorization).require("ent-acme", authentication, RealtimeMessageService.READ))
                .isInstanceOf(AccessDeniedException.class);
        verify(authorization, never()).decision(any(), any(), any(), any());
    }

    private static JwtAuthenticationToken token(Instant expiry, String capabilityTenant) {
        var builder = Jwt.withTokenValue("realtime-access-fixture").header("alg", "none")
                .subject("reader").issuedAt(Instant.now().minusSeconds(600))
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of(capabilityTenant, List.of(RealtimeMessageService.READ)));
        if (expiry != null) builder.expiresAt(expiry);
        return new JwtAuthenticationToken(builder.build(),
                List.of(new SimpleGrantedAuthority("SCOPE_" + RealtimeMessageService.READ)));
    }
}
