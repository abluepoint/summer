package com.abluepoint.summer.sample.realtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RealtimeWebSocketRevocationTest {
    private final AtomicBoolean active = new AtomicBoolean(true);
    private RealtimeWebSocketHub hub;
    private RealtimeWebSocketHandler handler;
    private WebSocketSession session;

    @BeforeEach void connect() {
        var mapper = new ObjectMapper();
        var authorization = mock(com.abluepoint.summer.sample.authorization.AuthorizationService.class);
        when(authorization.decision(any(), any(), any(), any())).thenReturn(
                new com.abluepoint.summer.sample.authorization.AuthorizationService.Decision(true, "ROLE_ALLOW"));
        hub = new RealtimeWebSocketHub(mapper, new RealtimeAccess(authorization), mock(org.springframework.beans.factory.ObjectProvider.class));
        handler = new RealtimeWebSocketHandler(hub, mock(RealtimeMessageService.class), mapper);
        var jwt = Jwt.withTokenValue("realtime-session-fixture").header("alg", "none")
                .subject("reader").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                .claim("enterprise_memberships", List.of(Map.of("enterpriseId", "ent-acme", "status", "ACTIVE")))
                .claim("enterprise_capabilities", Map.of("ent-acme", List.of(RealtimeMessageService.READ))).build();
        var attributes = new HashMap<String, Object>();
        attributes.put("enterpriseId", "ent-acme");
        attributes.put("authentication", new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("SCOPE_" + RealtimeMessageService.READ))));
        attributes.put("sessionActive", (BooleanSupplier) active::get);
        session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        hub.register("ent-acme", session);
        active.set(false);
    }

    @AfterEach void shutdown() { hub.shutdown(); }

    @Test void revokedSessionCannotRespondToPing() throws Exception {
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"ping\"}"));
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(session, never()).sendMessage(any());
    }

    @Test void revokedSessionCannotReceiveBroadcast() throws Exception {
        hub.broadcast("ent-acme", Map.of("type", "message", "message", Map.of("sequence", 1)));
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(session, never()).sendMessage(any());
    }

    @Test void idleRevokedSessionClosesOnHeartbeat() throws Exception {
        ReflectionTestUtils.invokeMethod(hub, "heartbeat");
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(session, never()).sendMessage(any());
    }

    @Test void unavailableSessionStoreFailsClosed() throws Exception {
        session.getAttributes().put("sessionActive", (BooleanSupplier) () -> {
            throw new IllegalStateException("session storage unavailable");
        });
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"ping\"}"));
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(session, never()).sendMessage(any());
    }
}
