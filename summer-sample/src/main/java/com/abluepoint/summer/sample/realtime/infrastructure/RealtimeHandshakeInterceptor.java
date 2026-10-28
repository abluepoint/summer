package com.abluepoint.summer.sample.realtime.infrastructure;
import com.abluepoint.summer.sample.realtime.domain.RealtimeMessageCursorException;
import com.abluepoint.summer.sample.realtime.application.RealtimeMessageService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {
    private final RealtimeAccess access;
    private final com.abluepoint.summer.sample.identity.infrastructure.stream.SessionStreamAccess sessions;

    public RealtimeHandshakeInterceptor(RealtimeAccess access,
                                 com.abluepoint.summer.sample.identity.infrastructure.stream.SessionStreamAccess sessions) {
        this.access = access;
        this.sessions = sessions;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        String enterpriseId = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("enterpriseId");
        Authentication authentication = request instanceof ServletServerHttpRequest servlet
                ? (Authentication) servlet.getServletRequest().getUserPrincipal() : null;
        if (!(authentication instanceof JwtAuthenticationToken)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED); return false;
        }
        try { access.require(enterpriseId, authentication, RealtimeMessageService.READ); }
        catch (RuntimeException denied) { response.setStatusCode(HttpStatus.FORBIDDEN); return false; }
        long after;
        try { after = RealtimeMessageService.cursor(UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("after")); }
        catch (RealtimeMessageCursorException invalid) { response.setStatusCode(HttpStatus.BAD_REQUEST); return false; }
        attributes.put("enterpriseId", enterpriseId);
        attributes.put("after", after);
        attributes.put("authentication", authentication);
        attributes.put("sessionActive", sessions.capture(
                ((ServletServerHttpRequest) request).getServletRequest(), authentication));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {}
}
