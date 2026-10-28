package com.abluepoint.summer.sample.task.application;
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

@Service
public class TaskProgressEventService {
 private final TaskProgressEventRepository repository;
 private final ObjectMapper objectMapper;
 private final com.abluepoint.summer.sample.task.infrastructure.TaskProgressStreams streams;
 public TaskProgressEventService(TaskDefinitionRepository definitions, TaskProgressEventRepository repository,
   ObjectMapper objectMapper, TaskProgressAccess access) {
  this.repository=repository; this.objectMapper=objectMapper;
  this.streams=new com.abluepoint.summer.sample.task.infrastructure.TaskProgressStreams(definitions,repository,objectMapper,access);
 }
 public SseEmitter subscribe(String enterpriseId, String taskId, long lastEventId, org.springframework.security.core.Authentication authentication) { return streams.subscribe(enterpriseId,taskId,lastEventId,authentication); }
 public SseEmitter subscribe(String enterpriseId, String taskId, long lastEventId, org.springframework.security.core.Authentication authentication, java.util.function.BooleanSupplier sessionActive) { return streams.subscribe(enterpriseId,taskId,lastEventId,authentication,sessionActive); }
 @PreDestroy public void shutdown() { streams.shutdown(); }
 private String writePayload(Map<String,Object> payload) { try { return objectMapper.writeValueAsString(payload); } catch (JacksonException failure) { throw new IllegalStateException("Unable to encode task progress", failure); } }
    public void started(TaskExecutionEntity execution) {
        publish(execution, "execution.started", Map.of("status", "RUNNING", "progress", 0));
        publish(execution, "execution.log", Map.of("level", "INFO", "message", "Task execution started"));
    }

    public void attempt(TaskExecutionEntity execution, int attempt, int maxAttempts) {
        int progress = Math.min(95, Math.max(1, ((attempt - 1) * 90) / Math.max(1, maxAttempts)));
        publish(execution, "execution.attempt", Map.of("status", "RUNNING", "attempt", attempt, "progress", progress));
        publish(execution, "execution.log", Map.of("level", "INFO", "message", "Task attempt started"));
    }

    public void progress(TaskExecutionEntity execution, int percent) {
        publish(execution, "execution.progress", Map.of("status", "RUNNING", "progress", percent,
                "attempt", execution.attempt()));
    }

    public void succeeded(TaskExecutionEntity execution) {
        publish(execution, "execution.succeeded", Map.of("status", "SUCCESS", "progress", 100));
        publish(execution, "execution.log", Map.of("level", "INFO", "message", "Task execution succeeded"));
    }

    public void failed(TaskExecutionEntity execution, String failureCode) {
        publish(execution, "execution.failed", Map.of("status", "FAILED", "failureCode", failureCode, "progress", 100));
        publish(execution, "execution.log", Map.of("level", "WARN", "message", "Task execution failed", "failureCode", failureCode));
    }

    private void publish(TaskExecutionEntity execution, String type, Map<String, Object> payload) {
        Map<String, Object> eventPayload = new HashMap<>(payload);
        eventPayload.put("taskId", execution.taskId());
        eventPayload.put("executionId", execution.executionId());
        eventPayload.put("eventType", type);
        repository.saveAndFlush(new TaskProgressEventEntity(execution.enterpriseId(), execution.taskId(),
                execution.executionId(), type, writePayload(eventPayload)));
        // Delivery polls committed rows; a slow socket never holds the task transaction or subscription lock.
    }


}
