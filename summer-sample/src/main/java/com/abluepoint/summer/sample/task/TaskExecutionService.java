package com.abluepoint.summer.sample.task;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Service
class TaskExecutionService {
    private static final ExecutorService HANDLERS = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "summer-task-handler");
        thread.setDaemon(true);
        return thread;
    });
    private final TaskDefinitionRepository definitions;
    private final TaskExecutionRepository executions;
    private final TaskAttemptRepository attempts;
    private final TrustedTaskRegistry registry;
    private final TaskProgressEventService progress;

    TaskExecutionService(TaskDefinitionRepository definitions, TaskExecutionRepository executions,
                         TaskAttemptRepository attempts, TrustedTaskRegistry registry,
                         TaskProgressEventService progress) {
        this.definitions = definitions; this.executions = executions; this.attempts = attempts; this.registry = registry;
        this.progress = progress;
    }

    @Transactional
    void execute(String taskId, String fireInstanceId) {
        execute(taskId, fireInstanceId, fireInstanceId, Instant.now(), false);
    }

    @Transactional
    void execute(String taskId, String fireInstanceId, String idempotencyKey, Instant scheduledFireTime, boolean recovering) {
        TaskDefinitionEntity task = definitions.findById(taskId).orElseThrow(TaskNotFoundException::new);
        String key = boundedKey(idempotencyKey, taskId + ":" + scheduledFireTime.toEpochMilli());
        TaskExecutionEntity execution = findOrCreate(task, fireInstanceId, key, scheduledFireTime, recovering);
        if (execution.status() != TaskExecutionStatus.RUNNING) return;
        if (execution.attempt() > 0 && execution.leaseUntil() != null && execution.leaseUntil().isAfter(Instant.now())) return;

        execution.claim(nodeId(), Instant.now().plusMillis(task.timeoutMs() + 5_000), recovering);
        executions.saveAndFlush(execution);
        progress.started(execution);
        Map<String, Object> parameters = readParameters(task.parametersJson());
        parameters = withIdempotencyKey(parameters, key);
        for (int number = execution.attempt() + 1; number <= task.maxRetries() + 1; number++) {
            execution.attempt(number);
            TaskAttemptEntity attempt = new TaskAttemptEntity(UUID.randomUUID().toString(), execution.executionId(), number, nodeId());
            attempts.saveAndFlush(attempt);
            progress.attempt(execution, number, task.maxRetries() + 1);
            try {
                runWithTimeout(task, parameters);
                attempt.success();
                execution.success();
                attempts.saveAndFlush(attempt);
                executions.saveAndFlush(execution);
                progress.succeeded(execution);
                return;
            } catch (TaskTimedOutException timeout) {
                attempt.failure("TASK_TIMEOUT", TaskExecutionStatus.FAILED);
                if (number == task.maxRetries() + 1) {
                    execution.failure("TASK_TIMEOUT");
                    progress.failed(execution, "TASK_TIMEOUT");
                }
            } catch (RuntimeException failure) {
                attempt.failure("TASK_HANDLER_FAILED", TaskExecutionStatus.FAILED);
                if (number == task.maxRetries() + 1) {
                    execution.failure("TASK_HANDLER_FAILED");
                    progress.failed(execution, "TASK_HANDLER_FAILED");
                }
            }
            attempts.saveAndFlush(attempt);
            executions.saveAndFlush(execution);
            if (execution.status() != TaskExecutionStatus.RUNNING) return;
        }
    }

    private TaskExecutionEntity findOrCreate(TaskDefinitionEntity task, String fireInstanceId, String key,
                                              Instant scheduledFireTime, boolean recovering) {
        var existing = executions.findByTaskIdAndIdempotencyKey(task.taskId(), key);
        if (existing.isPresent()) {
            TaskExecutionEntity execution = existing.get();
            if (execution.status() != TaskExecutionStatus.RUNNING) return execution;
            if (execution.leaseUntil() != null && execution.leaseUntil().isAfter(Instant.now()) && !recovering) return execution;
            return execution;
        }
        try {
            return executions.saveAndFlush(new TaskExecutionEntity(UUID.randomUUID().toString(), task.taskId(),
                    task.enterpriseId(), fireInstanceId, key, nodeId(),
                    Instant.now().plusMillis(task.timeoutMs() + 5_000)));
        } catch (DataIntegrityViolationException duplicate) {
            return executions.findByTaskIdAndIdempotencyKey(task.taskId(), key).orElseThrow(() -> duplicate);
        }
    }

    private void runWithTimeout(TaskDefinitionEntity task, Map<String, Object> parameters) {
        Future<?> future = HANDLERS.submit(() -> registry.execute(task.taskType(), parameters));
        try {
            future.get(task.timeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            future.cancel(true);
            throw new TaskTimedOutException();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new TaskTimedOutException();
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new RuntimeException(cause);
        }
    }

    private static Map<String, Object> withIdempotencyKey(Map<String, Object> parameters, String key) {
        java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>(parameters);
        copy.put("_summerTaskIdempotencyKey", key);
        return java.util.Collections.unmodifiableMap(copy);
    }

    private String nodeId() {
        String value = System.getenv("HOSTNAME");
        return value == null || value.isBlank() ? "summer-node" : value.substring(0, Math.min(value.length(), 120));
    }

    private static String boundedKey(String key, String fallback) {
        String value = key == null || key.isBlank() ? fallback : key.trim();
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readParameters(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return new tools.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }
}

final class TaskTimedOutException extends RuntimeException {}
