package com.abluepoint.summer.sample.realtime.infrastructure;
import com.abluepoint.summer.sample.realtime.application.RealtimeMessageView;
import com.abluepoint.summer.sample.realtime.domain.RealtimeConnectionLimitException;
import com.abluepoint.summer.sample.realtime.application.RealtimeConnectionView;
import com.abluepoint.summer.sample.realtime.application.RealtimeMessageService;
import com.abluepoint.summer.sample.realtime.application.RealtimeMessagePageView;
import com.abluepoint.summer.sample.realtime.application.RealtimeMessagePublishedEvent;

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
public class RealtimeWebSocketHub implements com.abluepoint.summer.sample.realtime.application.RealtimeConnectionMetrics {
    static final int MAX_CONNECTIONS_PER_ENTERPRISE = 32;
    private final ObjectMapper objectMapper;
    private final RealtimeAccess access;
    private final org.springframework.beans.factory.ObjectProvider<RealtimeMessageService> messages;
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    @org.springframework.beans.factory.annotation.Value("${summer.sample.realtime.idle-timeout-ms:60000}")
    private long idleTimeoutMillis = 60_000;
    @org.springframework.beans.factory.annotation.Value("${summer.sample.realtime.send-timeout-ms:5000}")
    private long sendTimeoutMillis = 5_000;
    private final ScheduledExecutorService heartbeats = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "summer-realtime-heartbeat"); t.setDaemon(true); return t;
    });

    public RealtimeWebSocketHub(ObjectMapper objectMapper, RealtimeAccess access,
                         org.springframework.beans.factory.ObjectProvider<RealtimeMessageService> messages) {
        this.objectMapper = objectMapper;
        this.access = access;
        this.messages = messages;
        heartbeats.scheduleAtFixedRate(this::heartbeat, 15, 15, TimeUnit.SECONDS);
        heartbeats.scheduleAtFixedRate(this::expireIdleConnections, 1, 1, TimeUnit.SECONDS);
    }

    public void register(String enterpriseId, WebSocketSession session) {
        requireCurrent(session);
        touch(session);
        session.setTextMessageSizeLimit(128 * 1024);
        if (session instanceof org.springframework.web.socket.adapter.standard.StandardWebSocketSession standard) {
            // Tomcat's blocking write is bounded as well as the independent watchdog.
            standard.getNativeSession().getUserProperties().put("org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT", sendTimeoutMillis);
            standard.getNativeSession().getUserProperties().put("org.apache.tomcat.websocket.SESSION_CLOSE_TIMEOUT", 1_000L);
        }
        sessions.compute(enterpriseId, (key, existing) -> {
            Set<WebSocketSession> active = existing == null ? ConcurrentHashMap.newKeySet() : existing;
            if (active.size() >= MAX_CONNECTIONS_PER_ENTERPRISE) throw new RealtimeConnectionLimitException();
            active.add(session);
            return active;
        });
    }

    public void remove(String enterpriseId, WebSocketSession session) {
        sessions.computeIfPresent(enterpriseId, (key, active) -> {
            active.remove(session);
            return active.isEmpty() ? null : active;
        });
    }

    public void broadcast(String enterpriseId, Object payload) {
        Set<WebSocketSession> active = sessions.getOrDefault(enterpriseId, Set.of());
        // Notifications may arrive twice or out of order. Read the durable stream from
        // each connection's cursor instead of forwarding notification arrival order.
        for (WebSocketSession session : Set.copyOf(active)) replay(session);
    }

    @EventListener
    public void messagePublished(RealtimeMessagePublishedEvent event) {
        broadcast(event.message().enterpriseId(), Map.of("type", "message", "message", event.message()));
    }

    public RealtimeConnectionView status(String enterpriseId) {
        return new RealtimeConnectionView(enterpriseId, sessions.getOrDefault(enterpriseId, Set.of()).size(),
                MAX_CONNECTIONS_PER_ENTERPRISE, "AVAILABLE");
    }

    /** Aggregate gauge only; no enterprise or session identifiers are exposed. */
    public long activeConnectionCount() {
        return sessions.values().stream().flatMap(Collection::stream).filter(WebSocketSession::isOpen).count();
    }

    private void heartbeat() {
        String now = Instant.now().toString();
        sessions.values().stream().flatMap(Collection::stream).forEach(session -> {
            replay(session);
            send(session, "{\"type\":\"heartbeat\",\"at\":\"" + now + "\"}");
        });
    }

    public void touch(WebSocketSession session) {
        session.getAttributes().put("lastClientActivity", System.nanoTime());
    }

    private void expireIdleConnections() {
        long now = System.nanoTime();
        sessions.values().stream().flatMap(Collection::stream).forEach(session -> {
            Object sending = session.getAttributes().get("sendStarted");
            if (sending instanceof Long start && TimeUnit.NANOSECONDS.toMillis(now - start) >= sendTimeoutMillis) {
                closeFailed(session);
                return;
            }
            Object activity = session.getAttributes().get("lastClientActivity");
            if (activity instanceof Long last && TimeUnit.NANOSECONDS.toMillis(now - last) >= idleTimeoutMillis) {
                close(session, new CloseStatus(1001, "idle timeout"));
            }
        });
    }

    public void send(WebSocketSession session, Object payload) {
        try { send(session, objectMapper.writeValueAsString(payload)); }
        catch (JacksonException failed) { closeFailed(session); }
    }

    public void connected(WebSocketSession session, Object payload) {
        synchronized (session) {
            send(session, payload);
            session.getAttributes().put("deliveryReady", true);
            replay(session);
        }
    }

    public void replay(WebSocketSession session) {
        if (!session.isOpen() || Boolean.TRUE.equals(session.getAttributes().get("deliveryClosed"))) return;
        try {
            synchronized (session) {
                requireCurrent(session);
                if (!Boolean.TRUE.equals(session.getAttributes().get("deliveryReady"))) return;
                String tenant = (String) session.getAttributes().get("enterpriseId");
                var auth = (org.springframework.security.core.Authentication) session.getAttributes().get("authentication");
                long cursor = ((Number) session.getAttributes().getOrDefault("deliveredAfter", session.getAttributes().getOrDefault("after", 0L))).longValue();
                RealtimeMessagePageView page;
                do {
                    page = messages.getObject().list(tenant, cursor, auth);
                    for (RealtimeMessageView message : page.items()) {
                        if (!send(session, objectMapper.writeValueAsString(Map.of("type", "message", "message", message)))) return;
                        cursor = message.sequence();
                        session.getAttributes().put("deliveredAfter", cursor);
                    }
                } while (page.hasMore() && !page.items().isEmpty());
            }
        } catch (org.springframework.security.access.AccessDeniedException denied) { closeDenied(session); }
        catch (RuntimeException unavailable) { closeFailed(session); }
    }

    private boolean send(WebSocketSession session, String json) {
        if (!session.isOpen() || Boolean.TRUE.equals(session.getAttributes().get("deliveryClosed"))) return false;
        try {
            synchronized (session) {
                requireCurrent(session);
                session.getAttributes().put("sendStarted", System.nanoTime());
                try { session.sendMessage(new TextMessage(json)); }
                finally { session.getAttributes().remove("sendStarted"); }
            }
            return true;
        }
        catch (org.springframework.security.access.AccessDeniedException denied) { closeDenied(session); }
        catch (IOException e) { closeFailed(session); }
        return false;
    }

    private void closeFailed(WebSocketSession session) {
        close(session, CloseStatus.SERVER_ERROR);
    }

    public void requireCurrent(WebSocketSession session) {
        try {
            Object lease = session.getAttributes().get("sessionActive");
            if (!(lease instanceof java.util.function.BooleanSupplier active) || !active.getAsBoolean()) {
                throw new org.springframework.security.access.AccessDeniedException("Message session revoked");
            }
            access.require((String) session.getAttributes().get("enterpriseId"),
                    (org.springframework.security.core.Authentication) session.getAttributes().get("authentication"),
                    RealtimeMessageService.READ);
        } catch (RuntimeException deniedOrUnavailable) {
            throw new org.springframework.security.access.AccessDeniedException("Message access unavailable", deniedOrUnavailable);
        }
    }

    public void closeDenied(WebSocketSession session) {
        close(session, CloseStatus.POLICY_VIOLATION);
    }

    private void close(WebSocketSession session, CloseStatus status) {
        if (Boolean.TRUE.equals(session.getAttributes().put("deliveryClosed", true))) return;
        Object enterpriseId = session.getAttributes().get("enterpriseId");
        if (enterpriseId instanceof String enterprise) remove(enterprise, session);
        try { session.close(status); } catch (IOException ignored) { }
    }

    @PreDestroy
    public void shutdown() {
        heartbeats.shutdownNow();
        sessions.values().stream().flatMap(Collection::stream).forEach(s -> { try { s.close(CloseStatus.GOING_AWAY); } catch (IOException ignored) {} });
    }
}
