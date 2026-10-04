package com.abluepoint.summer.sample.task;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
class TaskProgressEventService {
    private static final int MAX_CONNECTIONS_PER_ENTERPRISE = 32;
    private static final int MAX_REPLAY_EVENTS = 500;
    private static final long HEARTBEAT_SECONDS = 15L;
    private final TaskDefinitionRepository definitions;
    private final TaskProgressEventRepository repository;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "summer-task-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Set<Connection>> connections = new ConcurrentHashMap<>();
    private final Object subscriptionLock = new Object();

    TaskProgressEventService(TaskDefinitionRepository definitions, TaskProgressEventRepository repository,
                             ObjectMapper objectMapper) {
        this.definitions = definitions;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    SseEmitter subscribe(String enterpriseId, String taskId, long lastEventId) {
        requireTask(enterpriseId, taskId);
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(30));
        Connection connection = new Connection(enterpriseId, taskId, emitter);
        synchronized (subscriptionLock) {
            Set<Connection> active = connections.computeIfAbsent(enterpriseId, ignored -> ConcurrentHashMap.newKeySet());
            if (active.size() >= MAX_CONNECTIONS_PER_ENTERPRISE) throw new TaskProgressConnectionLimitException();
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

    void started(TaskExecutionEntity execution) {
        publish(execution, "execution.started", Map.of("status", "RUNNING", "progress", 0));
        publish(execution, "execution.log", Map.of("level", "INFO", "message", "Task execution started"));
    }

    void attempt(TaskExecutionEntity execution, int attempt, int maxAttempts) {
        int progress = Math.min(95, Math.max(1, (attempt * 90) / Math.max(1, maxAttempts)));
        publish(execution, "execution.attempt", Map.of("status", "RUNNING", "attempt", attempt, "progress", progress));
        publish(execution, "execution.log", Map.of("level", "INFO", "message", "Task attempt started"));
    }

    void succeeded(TaskExecutionEntity execution) {
        publish(execution, "execution.succeeded", Map.of("status", "SUCCESS", "progress", 100));
        publish(execution, "execution.log", Map.of("level", "INFO", "message", "Task execution succeeded"));
    }

    void failed(TaskExecutionEntity execution, String failureCode) {
        publish(execution, "execution.failed", Map.of("status", "FAILED", "failureCode", failureCode, "progress", 100));
        publish(execution, "execution.log", Map.of("level", "WARN", "message", "Task execution failed", "failureCode", failureCode));
    }

    private void publish(TaskExecutionEntity execution, String type, Map<String, Object> payload) {
        synchronized (subscriptionLock) {
            Map<String, Object> eventPayload = new HashMap<>(payload);
            eventPayload.put("taskId", execution.taskId());
            eventPayload.put("executionId", execution.executionId());
            eventPayload.put("eventType", type);
            String json = writePayload(eventPayload);
            TaskProgressEventEntity event = repository.saveAndFlush(new TaskProgressEventEntity(
                    execution.enterpriseId(), execution.taskId(), execution.executionId(), type, json));
            Set<Connection> active = connections.get(execution.enterpriseId());
            if (active == null) return;
            for (Connection connection : List.copyOf(active)) send(connection, event);
        }
    }

    private void sendConnected(Connection connection, long lastEventId) throws IOException {
        List<TaskProgressEventEntity> all = repository.findAllByEnterpriseIdAndTaskIdOrderByEventIdAsc(
                connection.enterpriseId, connection.taskId);
        long latest = all.stream().map(TaskProgressEventEntity::eventId).filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(0L);
        connection.emitter.send(SseEmitter.event().name("connected").data(writePayload(Map.of(
                "taskId", connection.taskId, "enterpriseId", connection.enterpriseId,
                "latestEventId", latest, "lastEventId", lastEventId))));
    }

    private void replay(Connection connection, long lastEventId) throws IOException {
        List<TaskProgressEventEntity> all = repository.findAllByEnterpriseIdAndTaskIdOrderByEventIdAsc(
                connection.enterpriseId, connection.taskId);
        int start = Math.max(0, all.size() - MAX_REPLAY_EVENTS);
        List<TaskProgressEventEntity> window = all.subList(start, all.size());
        if (!window.isEmpty() && lastEventId > 0 && lastEventId < window.get(0).eventId() - 1) {
            connection.emitter.send(SseEmitter.event().name("resync").data(writePayload(Map.of(
                    "reason", "EVENT_WINDOW_EXCEEDED", "taskId", connection.taskId,
                    "latestEventId", window.get(window.size() - 1).eventId()))));
            return;
        }
        for (TaskProgressEventEntity event : window) {
            if (event.eventId() > lastEventId) send(connection, event);
        }
    }

    private void heartbeat(Connection connection) {
        try {
            connection.emitter.send(SseEmitter.event().comment("keep-alive"));
        } catch (IOException failure) {
            remove(connection);
            connection.emitter.completeWithError(failure);
        }
    }

    private void send(Connection connection, TaskProgressEventEntity event) {
        try {
            connection.emitter.send(SseEmitter.event().id(String.valueOf(event.eventId()))
                    .name(event.eventType()).data(event.payloadJson()));
        } catch (IOException failure) {
            remove(connection);
            connection.emitter.completeWithError(failure);
        }
    }

    private void remove(Connection connection) {
        Set<Connection> active = connections.get(connection.enterpriseId);
        if (active != null) {
            active.remove(connection);
            if (active.isEmpty()) connections.remove(connection.enterpriseId, active);
        }
        ScheduledFuture<?> heartbeat = connection.heartbeat;
        if (heartbeat != null) heartbeat.cancel(false);
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
    void shutdown() { scheduler.shutdownNow(); connections.values().stream().flatMap(Collection::stream).forEach(c -> c.emitter.complete()); }

    private static final class Connection {
        private final String enterpriseId;
        private final String taskId;
        private final SseEmitter emitter;
        private ScheduledFuture<?> heartbeat;
        private Connection(String enterpriseId, String taskId, SseEmitter emitter) {
            this.enterpriseId = enterpriseId; this.taskId = taskId; this.emitter = emitter;
        }
    }
}

final class TaskProgressConnectionLimitException extends RuntimeException {}
