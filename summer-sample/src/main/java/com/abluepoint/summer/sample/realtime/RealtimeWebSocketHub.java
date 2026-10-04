package com.abluepoint.summer.sample.realtime;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
class RealtimeWebSocketHub {
    static final int MAX_CONNECTIONS_PER_ENTERPRISE = 32;
    private final ObjectMapper objectMapper;
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "summer-realtime-heartbeat"); t.setDaemon(true); return t;
    });

    RealtimeWebSocketHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        heartbeats.scheduleAtFixedRate(this::heartbeat, 15, 15, TimeUnit.SECONDS);
    }

    void register(String enterpriseId, WebSocketSession session) {
        Set<WebSocketSession> active = sessions.computeIfAbsent(enterpriseId, ignored -> ConcurrentHashMap.newKeySet());
        if (active.size() >= MAX_CONNECTIONS_PER_ENTERPRISE) {
            throw new RealtimeConnectionLimitException();
        }
        active.add(session);
    }

    void remove(String enterpriseId, WebSocketSession session) {
        Set<WebSocketSession> active = sessions.get(enterpriseId);
        if (active != null) { active.remove(session); if (active.isEmpty()) sessions.remove(enterpriseId, active); }
    }

    void broadcast(String enterpriseId, Object payload) {
        String json;
        try { json = objectMapper.writeValueAsString(payload); }
        catch (JacksonException e) { return; }
        Set<WebSocketSession> active = sessions.getOrDefault(enterpriseId, Set.of());
        for (WebSocketSession session : Set.copyOf(active)) send(session, json);
    }

    @EventListener
    void messagePublished(RealtimeMessagePublishedEvent event) {
        broadcast(event.message().enterpriseId(), Map.of("type", "message", "message", event.message()));
    }

    RealtimeConnectionView status(String enterpriseId) {
        return new RealtimeConnectionView(enterpriseId, sessions.getOrDefault(enterpriseId, Set.of()).size(),
                MAX_CONNECTIONS_PER_ENTERPRISE, "AVAILABLE");
    }

    private void heartbeat() {
        String now = Instant.now().toString();
        sessions.values().stream().flatMap(Collection::stream).forEach(session -> send(session, "{\"type\":\"heartbeat\",\"at\":\"" + now + "\"}"));
    }

    private void send(WebSocketSession session, String json) {
        if (!session.isOpen()) return;
        try { synchronized (session) { session.sendMessage(new TextMessage(json)); } }
        catch (IOException e) { try { session.close(CloseStatus.SERVER_ERROR); } catch (IOException ignored) {} }
    }

    @PreDestroy
    void shutdown() {
        heartbeats.shutdownNow();
        sessions.values().stream().flatMap(Collection::stream).forEach(s -> { try { s.close(CloseStatus.GOING_AWAY); } catch (IOException ignored) {} });
    }
}

final class RealtimeConnectionLimitException extends RuntimeException {}
