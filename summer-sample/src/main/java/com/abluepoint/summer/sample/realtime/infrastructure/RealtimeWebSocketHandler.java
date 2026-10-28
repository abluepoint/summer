package com.abluepoint.summer.sample.realtime.infrastructure;
import com.abluepoint.summer.sample.realtime.application.RealtimeMessageView;
import com.abluepoint.summer.sample.realtime.domain.RealtimeConnectionLimitException;
import com.abluepoint.summer.sample.realtime.domain.RealtimeMessageValidationException;
import com.abluepoint.summer.sample.realtime.domain.RealtimeMessageNotFoundException;
import com.abluepoint.summer.sample.realtime.application.RealtimeMessageService;
import com.abluepoint.summer.sample.realtime.application.RealtimeMessageCreateCommand;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {
    private final RealtimeWebSocketHub hub;
    private final RealtimeMessageService messages;
    private final ObjectMapper objectMapper;

    public RealtimeWebSocketHandler(RealtimeWebSocketHub hub, RealtimeMessageService messages, ObjectMapper objectMapper) {
        this.hub = hub; this.messages = messages; this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String enterpriseId = (String) session.getAttributes().get("enterpriseId");
        try { hub.register(enterpriseId, session); }
        catch (RealtimeConnectionLimitException e) { session.close(new CloseStatus(1013, "connection limit")); return; }
        catch (org.springframework.security.access.AccessDeniedException denied) { hub.closeDenied(session); return; }
        long after = (long) session.getAttributes().getOrDefault("after", 0L);
        hub.connected(session, Map.of("type", "connected", "enterpriseId", enterpriseId,
                "after", after, "connections", hub.status(enterpriseId).connections()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try { hub.requireCurrent(session); }
        catch (org.springframework.security.access.AccessDeniedException denied) { hub.closeDenied(session); return; }
        hub.touch(session);
        String enterpriseId = (String) session.getAttributes().get("enterpriseId");
        var authentication = (org.springframework.security.core.Authentication) session.getAttributes().get("authentication");
        Map<String, Object> command;
        try { command = objectMapper.readValue(message.getPayload(), Map.class); }
        catch (RuntimeException e) { protocolError(session, "SUMMER_MESSAGE_INVALID", "Message must be valid JSON"); return; }
        String type = command.get("type") == null ? "" : String.valueOf(command.get("type"));
        try {
            if ("ping".equals(type)) { hub.send(session, Map.of("type", "pong")); return; }
            if ("send".equals(type)) {
                RealtimeMessageCreateCommand request = new RealtimeMessageCreateCommand(
                        string(command.get("clientMessageId")), string(command.get("recipient")),
                        command.get("payload") instanceof Map<?, ?> map ? castMap(map) : Map.of(),
                        command.get("ttlSeconds") instanceof Number n ? n.intValue() : null);
                RealtimeMessageView result = messages.send(enterpriseId, request, authentication);
                hub.send(session, Map.of("type", "ack", "clientMessageId", result.clientMessageId(),
                        "messageId", result.messageId(), "status", result.status(), "sequence", result.sequence(), "duplicate", result.duplicate()));
                return;
            }
            if ("ack".equals(type)) {
                RealtimeMessageView result = messages.acknowledge(enterpriseId, string(command.get("messageId")), authentication);
                hub.send(session, Map.of("type", "ack", "messageId", result.messageId(), "status", result.status()));
                return;
            }
            protocolError(session, "SUMMER_MESSAGE_INVALID", "Unsupported message type");
        } catch (RealtimeMessageValidationException e) { protocolError(session, "SUMMER_MESSAGE_INVALID", e.getMessage()); }
        catch (RealtimeMessageNotFoundException e) { protocolError(session, "SUMMER_NOT_FOUND", e.getMessage()); }
        catch (org.springframework.security.access.AccessDeniedException e) { session.close(CloseStatus.POLICY_VIOLATION); }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        hub.remove((String) session.getAttributes().get("enterpriseId"), session);
    }

    private void protocolError(WebSocketSession session, String code, String detail) throws Exception {
        hub.send(session, Map.of("type", "error", "code", code, "message", detail));
    }

    private static String string(Object value) { return value == null ? null : String.valueOf(value); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> value) { return (Map<String, Object>) value; }
}
