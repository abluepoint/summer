package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;

import com.abluepoint.summer.sample.authorization.DataScopeService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
class DictionaryNotificationService {
    private static final int MAX_CONNECTIONS_PER_ENTERPRISE = 32;
    private static final int MAX_REPLAY_EVENTS = 500;
    private static final long HEARTBEAT_SECONDS = 15L;

    private final DictionaryNotificationRepository repository;
    private final DictionaryTypeRepository types;
    private final DataScopeService dataScopes;
    private final com.abluepoint.summer.sample.configuration.ConfigurationActionAccess actions;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "summer-dictionary-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Set<Connection>> connections = new ConcurrentHashMap<>();
    private final Object subscriptionLock = new Object();

    DictionaryNotificationService(DictionaryNotificationRepository repository, DictionaryTypeRepository types,
                                   DataScopeService dataScopes, ObjectMapper objectMapper,
                                   ApplicationEventPublisher events, com.abluepoint.summer.sample.configuration.ConfigurationActionAccess actions) {
        this.repository = repository;
        this.types = types;
        this.dataScopes = dataScopes;
        this.objectMapper = objectMapper;
        this.events = events;
        this.actions = actions;
    }

    void record(String tenantId, String dictionaryCode, String eventType, long version, String requestId) {
        String payload = payload(tenantId, dictionaryCode, eventType, version, requestId);
        DictionaryNotificationEventEntity event = repository.saveAndFlush(new DictionaryNotificationEventEntity(
                tenantId, dictionaryCode, eventType, version, requestId, payload));
        events.publishEvent(new DictionaryNotificationCreated(event.eventId(), event.tenantId(),
                event.dictionaryCode(), event.eventType(), event.version(), event.payloadJson()));
    }

    SseEmitter subscribe(String enterpriseId, String dictionaryCode, long lastEventId, Authentication authentication) {
        requireReadable(enterpriseId, dictionaryCode, authentication);
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(30));
        Connection connection = new Connection(enterpriseId, dictionaryCode, emitter, authentication, lastEventId);
        synchronized (subscriptionLock) {
            Set<Connection> active = connections.computeIfAbsent(enterpriseId, ignored -> ConcurrentHashMap.newKeySet());
            if (active.size() >= MAX_CONNECTIONS_PER_ENTERPRISE) throw SummerException.withMessage(SampleTransportErrorCode.DICTIONARY_SSE_LIMIT, "Too many dictionary notification connections");
            active.add(connection);
            emitter.onCompletion(() -> remove(connection));
            emitter.onTimeout(() -> remove(connection));
            emitter.onError(ignored -> remove(connection));
            try {
                sendConnected(connection, lastEventId);
                replay(connection, lastEventId);
            } catch (IOException failure) {
                remove(connection);
                emitter.completeWithError(failure);
            }
            connection.heartbeat = scheduler.scheduleAtFixedRate(() -> heartbeat(connection),
                    HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        }
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void publishAfterCommit(DictionaryNotificationCreated event) {
        synchronized (subscriptionLock) {
            Set<Connection> active = connections.get(event.tenantId());
            if (active == null) return;
            DictionaryNotificationEventEntity persisted = new DictionaryNotificationEventEntity(
                    event.tenantId(), event.dictionaryCode(), event.eventType(), event.version(), null, event.payloadJson());
            persisted.assignEventId(event.eventId());
            for (Connection connection : List.copyOf(active)) {
                if (connection.dictionaryCode.equals(event.dictionaryCode())) send(connection, persisted);
            }
        }
    }

    private void requireReadable(String enterpriseId, String dictionaryCode, Authentication authentication) {
        com.abluepoint.summer.sample.configuration.ConfigurationTenantContext.requireMembership(enterpriseId, authentication);
        actions.requirePublic(enterpriseId, "dictionaries", authentication);
        DataScopeService.ScopeFilter scope = dataScopes.filter(enterpriseId, "dictionaries", authentication);
        boolean visible = types.findScoped(dictionaryCode, scope).isPresent();
        if (!visible) throw SummerException.withMessage(CommonErrorCode.NOT_FOUND, "Dictionary was not found");
    }

    private void sendConnected(Connection connection, long lastEventId) throws IOException {
        List<DictionaryNotificationEventEntity> all = repository.findTop500ByTenantIdAndDictionaryCodeOrderByEventIdDesc(
                connection.tenantId, connection.dictionaryCode);
        long latest = all.stream().map(DictionaryNotificationEventEntity::eventId).filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(0L);
        connection.emitter.send(SseEmitter.event().name("connected").data(payload(Map.of(
                "dictionaryCode", connection.dictionaryCode, "tenantId", connection.tenantId,
                "latestEventId", latest, "lastEventId", lastEventId))));
    }

    private void replay(Connection connection, long lastEventId) throws IOException {
        List<DictionaryNotificationEventEntity> window = repository
                .findTop500ByTenantIdAndDictionaryCodeOrderByEventIdDesc(connection.tenantId, connection.dictionaryCode);
        if (window.size() == MAX_REPLAY_EVENTS && lastEventId > 0 && lastEventId < window.get(window.size() - 1).eventId()) {
            connection.emitter.send(SseEmitter.event().name("resync").data(payload(Map.of(
                    "reason", "EVENT_WINDOW_EXCEEDED", "dictionaryCode", connection.dictionaryCode,
                    "latestEventId", window.get(0).eventId()))));
            return;
        }
        window = window.stream().sorted(Comparator.comparing(DictionaryNotificationEventEntity::eventId)).toList();
        for (DictionaryNotificationEventEntity event : window) {
            if (event.eventId() > lastEventId) send(connection, event);
        }
    }

    private void heartbeat(Connection connection) {
        try {
            requireReadable(connection.tenantId, connection.dictionaryCode, connection.authentication);
            replay(connection, connection.lastEventId);
            connection.emitter.send(SseEmitter.event().comment("keep-alive"));
        } catch (IOException | RuntimeException failure) {
            remove(connection);
            connection.emitter.completeWithError(failure);
        }
    }

    private synchronized void send(Connection connection, DictionaryNotificationEventEntity event) {
        try {
            if (event.eventId() <= connection.lastEventId) return;
            requireReadable(connection.tenantId, connection.dictionaryCode, connection.authentication);
            connection.emitter.send(SseEmitter.event().id(String.valueOf(event.eventId()))
                    .name(event.eventType()).data(event.payloadJson()));
            connection.lastEventId = event.eventId();
        } catch (IOException | RuntimeException failure) {
            remove(connection);
            connection.emitter.completeWithError(failure);
        }
    }

    private void remove(Connection connection) {
        Set<Connection> active = connections.get(connection.tenantId);
        if (active != null) {
            active.remove(connection);
            if (active.isEmpty()) connections.remove(connection.tenantId, active);
        }
        ScheduledFuture<?> heartbeat = connection.heartbeat;
        if (heartbeat != null) heartbeat.cancel(false);
    }

    private String payload(String tenantId, String code, String type, long version, String requestId) {
        return payload(Map.of("tenantId", tenantId, "dictionaryCode", code, "eventType", type, "version", version,
                "requestId", requestId == null ? "" : requestId));
    }

    private String payload(Map<String, Object> value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException failure) { throw new IllegalStateException("Unable to encode dictionary notification", failure); }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
        connections.values().stream().flatMap(Collection::stream).forEach(c -> c.emitter.complete());
    }

    private static final class Connection {
        private final String tenantId;
        private final String dictionaryCode;
        private final SseEmitter emitter;
        private ScheduledFuture<?> heartbeat;
        private final Authentication authentication;
        private volatile long lastEventId;
        private Connection(String tenantId, String dictionaryCode, SseEmitter emitter, Authentication authentication, long lastEventId) {
            this.tenantId = tenantId;
            this.dictionaryCode = dictionaryCode;
            this.emitter = emitter;
            this.authentication = authentication;
            this.lastEventId = lastEventId;
        }
    }

    record DictionaryNotificationCreated(Long eventId, String tenantId, String dictionaryCode,
                                         String eventType, long version, String payloadJson) {}
}

