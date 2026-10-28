package com.abluepoint.summer.sample.task.infrastructure;
import com.abluepoint.summer.sample.task.infrastructure.TaskExecutionEntity;
import com.abluepoint.summer.sample.task.infrastructure.TaskProgressEventEntity;
import com.abluepoint.summer.sample.task.domain.TaskProgressConnectionLimitException;
import com.abluepoint.summer.sample.task.infrastructure.TaskProgressAccess;
import com.abluepoint.summer.sample.task.infrastructure.TaskProgressEventRepository;
import com.abluepoint.summer.sample.task.domain.TaskNotFoundException;
import com.abluepoint.summer.sample.task.infrastructure.TaskDefinitionRepository;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

public class TaskProgressStreams {
    private static final int MAX_CONNECTIONS_PER_ENTERPRISE = 32;
    private static final int MAX_REPLAY_EVENTS = 500;
    private static final int MAX_CONNECTIONS = 256;
    private static final long HEARTBEAT_SECONDS = 15L;
    private final TaskDefinitionRepository definitions;
    private final TaskProgressEventRepository repository;
    private final ObjectMapper objectMapper;
    private final TaskProgressAccess access;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "summer-task-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });
    // Admission and the per-connection delivery guard bound simultaneous writes.
    // No queued healthy stream should wait behind blocked socket writes.
    private final ThreadPoolExecutor delivery = new ThreadPoolExecutor(4, MAX_CONNECTIONS,
            30, TimeUnit.SECONDS, new SynchronousQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "summer-task-sse-delivery");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final Map<String, Set<Connection>> connections = new ConcurrentHashMap<>();
    private final Object subscriptionLock = new Object();

    public TaskProgressStreams(TaskDefinitionRepository definitions, TaskProgressEventRepository repository,
                             ObjectMapper objectMapper, TaskProgressAccess access) {
        this.definitions = definitions;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.access = access;
        scheduler.scheduleWithFixedDelay(this::pollCommitted, 500, 500, TimeUnit.MILLISECONDS);
    }

    public SseEmitter subscribe(String enterpriseId, String taskId, long lastEventId,
                         org.springframework.security.core.Authentication authentication) {
        return subscribe(enterpriseId, taskId, lastEventId, authentication, () -> true);
    }

    public SseEmitter subscribe(String enterpriseId, String taskId, long lastEventId,
                         org.springframework.security.core.Authentication authentication,
                         java.util.function.BooleanSupplier sessionActive) {
        requireTask(enterpriseId, taskId);
        access.requireReadable(enterpriseId, authentication);
        if (!sessionActive.getAsBoolean()) throw new org.springframework.security.access.AccessDeniedException("Task progress session expired");
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(30));
        Connection connection = new Connection(enterpriseId, taskId, emitter, authentication, sessionActive);
        connection.cursor = lastEventId;
        synchronized (subscriptionLock) {
            Set<Connection> active = connections.computeIfAbsent(enterpriseId, ignored -> ConcurrentHashMap.newKeySet());
            if (active.size() >= MAX_CONNECTIONS_PER_ENTERPRISE
                    || connections.values().stream().mapToInt(Set::size).sum() >= MAX_CONNECTIONS) {
                if (active.isEmpty()) connections.remove(enterpriseId, active);
                throw new TaskProgressConnectionLimitException();
            }
            active.add(connection);
            emitter.onCompletion(() -> remove(connection));
            emitter.onTimeout(() -> remove(connection));
            emitter.onError(ignored -> remove(connection));
        }
        try {
            List<TaskProgressEventEntity> window = repository.findTop501ByEnterpriseIdAndTaskIdOrderByEventIdDesc(
                    connection.enterpriseId, connection.taskId);
            sendConnected(connection, lastEventId, window);
            replay(connection, lastEventId, window);
            connection.ready = true;
        } catch (IOException | RuntimeException failure) {
            remove(connection);
            emitter.completeWithError(failure);
        }
        return emitter;
    }

    private void sendConnected(Connection connection, long lastEventId, List<TaskProgressEventEntity> descending) throws IOException {
        long latest = descending.isEmpty() ? 0L : descending.get(0).eventId();
        connection.emitter.send(SseEmitter.event().name("connected").data(writePayload(Map.of(
                "taskId", connection.taskId, "enterpriseId", connection.enterpriseId,
                "latestEventId", latest, "lastEventId", lastEventId))));
    }

    private void replay(Connection connection, long lastEventId, List<TaskProgressEventEntity> descending) throws IOException {
        List<TaskProgressEventEntity> window = new ArrayList<>(descending.subList(0, Math.min(MAX_REPLAY_EVENTS, descending.size())));
        Collections.reverse(window);
        long latest = window.isEmpty() ? 0L : window.get(window.size() - 1).eventId();
        if (lastEventId > latest || (descending.size() > MAX_REPLAY_EVENTS && lastEventId > 0
                && lastEventId < window.get(0).eventId() - 1)) {
            connection.emitter.send(SseEmitter.event().name("resync").data(writePayload(Map.of(
                    "reason", "EVENT_WINDOW_EXCEEDED", "taskId", connection.taskId, "latestEventId", latest))));
            connection.cursor = latest;
            return;
        }
        for (TaskProgressEventEntity event : window) if (event.eventId() > lastEventId) send(connection, event);
    }

    private void pollCommitted() {
        for (Set<Connection> active : connections.values()) for (Connection connection : List.copyOf(active)) {
            if (!connection.ready || connection.closed.get() || !connection.delivering.compareAndSet(false, true)) continue;
            try {
                delivery.execute(() -> {
                    try {
                        List<TaskProgressEventEntity> events = repository.findTop500ByEnterpriseIdAndTaskIdAndEventIdGreaterThanOrderByEventIdAsc(
                                connection.enterpriseId, connection.taskId, connection.cursor);
                        boolean heartbeatDue = System.nanoTime() - connection.lastHeartbeat >= TimeUnit.SECONDS.toNanos(HEARTBEAT_SECONDS);
                        if (!events.isEmpty() || heartbeatDue) {
                            if (!connection.sessionActive.getAsBoolean()) {
                                throw new org.springframework.security.access.AccessDeniedException("Task progress session revoked");
                            }
                            access.requireReadable(connection.enterpriseId, connection.authentication);
                        }
                        for (TaskProgressEventEntity event : events) send(connection, event);
                        if (!connection.closed.get() && heartbeatDue) {
                            connection.emitter.send(SseEmitter.event().comment("keep-alive"));
                            connection.lastHeartbeat = System.nanoTime();
                        }
                    } catch (org.springframework.security.access.AccessDeniedException revoked) {
                        remove(connection);
                        connection.emitter.complete();
                    } catch (IOException | IllegalStateException disconnected) {
                        remove(connection);
                        connection.emitter.completeWithError(disconnected);
                    } catch (RuntimeException unavailable) {
                        // A transient database failure keeps the cursor at the last successfully delivered row.
                    } finally {
                        connection.delivering.set(false);
                    }
                });
            } catch (RejectedExecutionException overloaded) {
                remove(connection);
                connection.delivering.set(false);
                connection.emitter.completeWithError(overloaded);
            }
        }
    }

    private void send(Connection connection, TaskProgressEventEntity event) throws IOException {
        if (connection.closed.get() || !connection.taskId.equals(event.taskId()) || event.eventId() <= connection.cursor) return;
        connection.emitter.send(SseEmitter.event().id(String.valueOf(event.eventId()))
                .name(event.eventType()).data(event.payloadJson()));
        connection.cursor = event.eventId();
    }

    private void remove(Connection connection) {
        connection.closed.set(true);
        synchronized (subscriptionLock) {
            Set<Connection> active = connections.get(connection.enterpriseId);
            if (active != null) {
                active.remove(connection);
                if (active.isEmpty()) connections.remove(connection.enterpriseId, active);
            }
        }
    }

    private void requireTask(String enterpriseId, String taskId) {
        if (definitions.findById(taskId).filter(task -> enterpriseId.equals(task.enterpriseId())).isEmpty()) {
            throw new TaskNotFoundException();
        }
    }

    private String writePayload(Map<String, Object> payload) {
        try { return objectMapper.writeValueAsString(payload); }
        catch (JacksonException failure) { throw new IllegalStateException("Unable to encode task progress", failure); }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        delivery.shutdownNow();
        connections.values().stream().flatMap(Collection::stream).toList().forEach(c -> { remove(c); c.emitter.complete(); });
    }

    private static final class Connection {
        private final String enterpriseId;
        private final String taskId;
        private final SseEmitter emitter;
        private final org.springframework.security.core.Authentication authentication;
        private final java.util.function.BooleanSupplier sessionActive;
        private final AtomicBoolean delivering = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile boolean ready;
        private volatile long cursor;
        private long lastHeartbeat = System.nanoTime();
        private Connection(String enterpriseId, String taskId, SseEmitter emitter,
                           org.springframework.security.core.Authentication authentication,
                           java.util.function.BooleanSupplier sessionActive) {
            this.enterpriseId = enterpriseId; this.taskId = taskId; this.emitter = emitter;
            this.authentication = authentication;
            this.sessionActive = sessionActive;
        }
    }
}

