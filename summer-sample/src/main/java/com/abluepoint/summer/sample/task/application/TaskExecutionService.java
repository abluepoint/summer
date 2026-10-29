package com.abluepoint.summer.sample.task.application;
import com.abluepoint.summer.sample.task.infrastructure.TaskDefinitionEntity;
import com.abluepoint.summer.sample.task.infrastructure.TaskAttemptRepository;
import com.abluepoint.summer.sample.task.infrastructure.TaskExecutionEntity;
import com.abluepoint.summer.sample.task.domain.TaskExecutionStatus;
import com.abluepoint.summer.sample.task.domain.TaskNotFoundException;
import com.abluepoint.summer.sample.task.domain.TaskTimedOutException;
import com.abluepoint.summer.sample.task.infrastructure.TaskExecutionRepository;
import com.abluepoint.summer.sample.task.infrastructure.TrustedTaskRegistry;
import com.abluepoint.summer.sample.task.infrastructure.TaskDefinitionRepository;
import com.abluepoint.summer.sample.task.infrastructure.TaskAttemptEntity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import static com.abluepoint.summer.sample.task.domain.TaskExecutionRules.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Service
public class TaskExecutionService {
    private final TaskDefinitionRepository definitions;
    private final TaskExecutionRepository executions;
    private final TaskAttemptRepository attempts;
    private final com.abluepoint.summer.sample.task.infrastructure.TaskHandlerExecutor handler;
    private final TaskProgressEventService progress;
    private final TransactionTemplate transactions;

    public TaskExecutionService(TaskDefinitionRepository definitions, TaskExecutionRepository executions,
                         TaskAttemptRepository attempts, com.abluepoint.summer.sample.task.infrastructure.TaskHandlerExecutor handler,
                         TaskProgressEventService progress, PlatformTransactionManager transactionManager) {
        this.definitions = definitions; this.executions = executions; this.attempts = attempts; this.handler = handler;
        this.progress = progress;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void execute(String taskId, String fireInstanceId) {
        execute(taskId, fireInstanceId, fireInstanceId, Instant.now(), false);
    }

    public void execute(String taskId, String fireInstanceId, String idempotencyKey, Instant scheduledFireTime, boolean recovering) {
        String key = boundedKey(idempotencyKey, taskId + ":" + scheduledFireTime.toEpochMilli());
        ExecutionRun run = transactions.execute(ignored -> claim(taskId, fireInstanceId, key, recovering));
        if (run == null) return;
        Map<String, Object> parameters = withExecutionContext(readParameters(run.task().parametersJson()), run.task(), key);
        for (int number = run.firstAttempt(); number <= run.maxAttempts(); number++) {
            int currentAttempt = number;
            String attemptId = transactions.execute(ignored -> beginAttempt(run, currentAttempt));
            if (attemptId == null) return;
            String failureCode = null;
            try {
                handler.execute(run.task().taskType(), run.task().timeoutMs(), parameters, value -> recordProgress(run, currentAttempt, value));
            } catch (TaskTimedOutException timeout) {
                failureCode = "TASK_TIMEOUT";
            } catch (RuntimeException failure) {
                failureCode = "TASK_HANDLER_FAILED";
            }
            String result = failureCode;
            Boolean retry = transactions.execute(ignored -> finishAttempt(run, currentAttempt, attemptId, result));
            if (!Boolean.TRUE.equals(retry)) return;
        }
    }

    private ExecutionRun claim(String taskId, String fireInstanceId, String key, boolean recovering) {
        // The definition lock serializes creation of this task's idempotency receipt on every node.
        TaskDefinitionEntity task = definitions.findForExecution(taskId).orElseThrow(TaskNotFoundException::new);
        var existing = executions.findClaimForUpdate(task.taskId(), key);
        String owner = nodeId() + "/" + UUID.randomUUID();
        TaskExecutionEntity execution = existing.orElseGet(() -> new TaskExecutionEntity(
                UUID.randomUUID().toString(), taskId, task.enterpriseId(), fireInstanceId, key,
                owner, Instant.now().plusMillis(task.timeoutMs() + 5_000)));
        if (existing.isPresent() && (!claimable(execution.status(), execution.leaseUntil(), recovering, Instant.now()))) {
            return null;
        }
        if (existing.isPresent()) {
            attempts.findAllByExecutionIdOrderByAttemptAsc(execution.executionId()).stream()
                    .filter(attempt -> attempt.status() == TaskExecutionStatus.RUNNING)
                    .forEach(attempt -> attempt.failure("TASK_NODE_LOST", TaskExecutionStatus.FAILED));
        }
        execution.claim(owner, Instant.now().plusMillis(task.timeoutMs() + 5_000), recovering);
        executions.saveAndFlush(execution);
        progress.started(execution);
        return new ExecutionRun(task, execution.executionId(), owner, execution.attempt() + 1,
                Math.max(task.maxRetries() + 1, execution.attempt() + 1));
    }

    private String beginAttempt(ExecutionRun run, int number) {
        TaskExecutionEntity execution = ownedExecution(run);
        if (execution == null) return null;
        execution.attempt(number);
        execution.claim(run.owner(), Instant.now().plusMillis(run.task().timeoutMs() + 5_000), false);
        String attemptId = UUID.randomUUID().toString();
        TaskAttemptEntity attempt = new TaskAttemptEntity(attemptId, execution.executionId(), number, nodeId());
        attempts.saveAndFlush(attempt);
        progress.attempt(execution, number, run.maxAttempts());
        return attemptId;
    }

    private boolean finishAttempt(ExecutionRun run, int number, String attemptId, String failureCode) {
        TaskExecutionEntity execution = ownedExecution(run);
        if (execution == null || execution.attempt() != number) return false;
        TaskAttemptEntity attempt = attempts.findById(attemptId).orElseThrow();
        if (failureCode == null) {
            attempt.success();
            execution.success();
            progress.succeeded(execution);
        } else {
            attempt.failure(failureCode, TaskExecutionStatus.FAILED);
            if (number == run.maxAttempts()) {
                execution.failure(failureCode);
                progress.failed(execution, failureCode);
            }
        }
        return execution.status() == TaskExecutionStatus.RUNNING;
    }

    private void recordProgress(ExecutionRun run, int number, int completedPercent) {
        transactions.executeWithoutResult(ignored -> {
            TaskExecutionEntity execution = ownedExecution(run);
            if (execution == null || execution.attempt() != number) return;
            int percent = completedPercent(number, completedPercent, run.maxAttempts());
            progress.progress(execution, percent);
        });
    }

    private TaskExecutionEntity ownedExecution(ExecutionRun run) {
        return executions.findForUpdate(run.executionId())
                .filter(execution -> execution.status() == TaskExecutionStatus.RUNNING && run.owner().equals(execution.nodeId()))
                .orElse(null);
    }

    private static Map<String, Object> withExecutionContext(Map<String, Object> parameters, TaskDefinitionEntity task, String key) {
        java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>(parameters);
        copy.put("_summerTaskIdempotencyKey", key);
        copy.put("_summerTaskId", task.taskId());
        copy.put("_summerTaskEnterpriseId", task.enterpriseId());
        return java.util.Collections.unmodifiableMap(copy);
    }

    private String nodeId() {
        String value = System.getenv("HOSTNAME");
        return value == null || value.isBlank() ? "summer-node" : value.substring(0, Math.min(value.length(), 80));
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

    private record ExecutionRun(TaskDefinitionEntity task, String executionId, String owner, int firstAttempt, int maxAttempts) {}
}
