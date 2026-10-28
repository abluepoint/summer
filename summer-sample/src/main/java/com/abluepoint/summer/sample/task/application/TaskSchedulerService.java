package com.abluepoint.summer.sample.task.application;
import com.abluepoint.summer.sample.task.infrastructure.TaskDefinitionEntity;
import com.abluepoint.summer.sample.task.infrastructure.TaskTriggerReceiptRepository;
import com.abluepoint.summer.sample.task.domain.TaskConflictException;
import com.abluepoint.summer.sample.task.infrastructure.TaskAttemptRepository;
import com.abluepoint.summer.sample.task.domain.TaskStatus;
import com.abluepoint.summer.sample.task.infrastructure.TaskExecutionEntity;
import com.abluepoint.summer.sample.task.infrastructure.TaskTriggerReceiptEntity;
import com.abluepoint.summer.sample.task.domain.TaskIdempotencyConflictException;
import com.abluepoint.summer.sample.task.domain.TaskNotFoundException;
import com.abluepoint.summer.sample.task.domain.TaskIdempotencyRequiredException;
import com.abluepoint.summer.sample.task.domain.TaskPreconditionException;
import com.abluepoint.summer.sample.task.infrastructure.TaskExecutionRepository;
import com.abluepoint.summer.sample.task.infrastructure.TrustedTaskRegistry;
import com.abluepoint.summer.sample.task.infrastructure.TaskDefinitionRepository;
import com.abluepoint.summer.sample.task.domain.TaskValidationException;
import com.abluepoint.summer.sample.task.domain.TaskUntrustedTypeException;

import com.abluepoint.summer.sample.customer.service.CustomerAuditService;
import com.abluepoint.summer.sample.task.domain.TaskSchedule;
import com.abluepoint.summer.sample.task.domain.TaskSchedulingPort;
import static com.abluepoint.summer.sample.task.domain.TaskExecutionRules.*;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import java.util.*;

@Service
public class TaskSchedulerService {
    private static final String GROUP = "summer-task";
    private final TaskDefinitionRepository definitions;
    private final TaskExecutionRepository executions;
    private final TaskAttemptRepository attempts;
    private final TaskTriggerReceiptRepository triggerReceipts;
    private final TrustedTaskRegistry registry;
    private final TaskSchedulingPort scheduler;
    private final ObjectMapper objectMapper;
    private final CustomerAuditService audit;

    public TaskSchedulerService(TaskDefinitionRepository definitions, TaskExecutionRepository executions,
                         TaskAttemptRepository attempts, TaskTriggerReceiptRepository triggerReceipts,
                         TrustedTaskRegistry registry, TaskSchedulingPort scheduler, ObjectMapper objectMapper,
                         CustomerAuditService audit) {
        this.definitions = definitions; this.executions = executions; this.attempts = attempts; this.triggerReceipts = triggerReceipts; this.registry = registry;
        this.scheduler = scheduler; this.objectMapper = objectMapper; this.audit = audit;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void schedulePersistedTasks() {
        definitions.findAllByStatus(TaskStatus.ACTIVE).forEach(task -> scheduler.scheduleExisting(scheduleOf(task)));
        scheduler.initializeRecovery();
    }

    @Transactional(readOnly = true)
    public List<TaskView> list(String enterpriseId) {
        requireTenant(enterpriseId);
        return definitions.findAllByEnterpriseIdOrderByUpdatedAtDesc(enterpriseId).stream().map(this::view).toList();
    }

    @Transactional
    public TaskView create(String enterpriseId, TaskCreateCommand request, String requestId, org.springframework.security.core.Authentication authentication) {
        requireTenant(enterpriseId); validate(request);
        if (definitions.findByEnterpriseIdAndName(enterpriseId, request.name().trim()).isPresent()) throw new TaskConflictException("Task name already exists");
        String id = UUID.randomUUID().toString();
        TaskDefinitionEntity task = new TaskDefinitionEntity(id, enterpriseId, request.name().trim(), request.taskType().trim(),
                request.cronExpression().trim(), json(request.parameters()), retries(request.maxRetries()), timeout(request.timeoutMs()));
        TaskView result = view(definitions.saveAndFlush(task));
        scheduler.schedule(scheduleOf(task));
        audit.recordTenantResourceSuccess(enterpriseId, "task:" + id, "TASK_CREATED", requestId, authentication);
        return result;
    }

    @Transactional
    public TaskView update(String enterpriseId, String taskId, TaskUpdateCommand request, String ifMatch, String requestId,
                    org.springframework.security.core.Authentication authentication) {
        TaskDefinitionEntity task = require(enterpriseId, taskId); etag(task, ifMatch);
        if (request == null) throw new TaskValidationException("Task update is required");
        String name = request.name() == null ? task.name() : request.name().trim();
        String cron = request.cronExpression() == null ? task.cronExpression() : request.cronExpression().trim();
        if (name.isBlank() || !scheduler.validCron(cron)) throw new TaskValidationException("Task name or cron expression is invalid");
        int maxRetries = request.maxRetries() == null ? task.maxRetries() : request.maxRetries();
        retries(maxRetries);
        long timeoutMs = request.timeoutMs() == null ? task.timeoutMs() : timeout(request.timeoutMs());
        task.update(name, cron, request.parameters() == null ? task.parametersJson() : json(request.parameters()), maxRetries, timeoutMs);
        TaskView result = view(definitions.saveAndFlush(task));
        scheduler.reschedule(scheduleOf(task));
        audit.recordTenantResourceSuccess(enterpriseId, "task:" + taskId, "TASK_UPDATED", requestId, authentication);
        return result;
    }

    @Transactional
    public TaskView setStatus(String enterpriseId, String taskId, TaskStatus status, String ifMatch, String requestId,
                       org.springframework.security.core.Authentication authentication) {
        TaskDefinitionEntity task = require(enterpriseId, taskId); etag(task, ifMatch); task.status(status);
        TaskView result = view(definitions.saveAndFlush(task));
        scheduler.setStatus(scheduleOf(task), status);
        audit.recordTenantResourceSuccess(enterpriseId, "task:" + taskId, status == TaskStatus.PAUSED ? "TASK_PAUSED" : "TASK_RESUMED", requestId, authentication);
        return result;
    }

    @Transactional(readOnly = true)
    public TaskExecutionPageView executions(String enterpriseId, String taskId) {
        require(enterpriseId, taskId);
        List<TaskExecutionView> values = executions.findAllByEnterpriseIdAndTaskIdOrderByStartedAtDesc(enterpriseId, taskId).stream().map(this::executionView).toList();
        return new TaskExecutionPageView(values, values.size());
    }

    public void trigger(String enterpriseId, String taskId, String idempotencyKey, String requestId, org.springframework.security.core.Authentication authentication) {
        TaskDefinitionEntity task = require(enterpriseId, taskId);
        requireTriggerable(task.status());
        String key = requireKey(idempotencyKey);
        String fingerprint = fingerprint(taskId);
        var prior = triggerReceipts.findByEnterpriseIdAndIdempotencyKey(enterpriseId, key);
        if (prior.isPresent()) {
            if (!prior.get().fingerprint().equals(fingerprint) || !prior.get().taskId().equals(taskId))
                throw new TaskIdempotencyConflictException("Idempotency-Key was already used for another task trigger");
            dispatchReserved(task, key, requestId, authentication);
            return;
        }
        try { triggerReceipts.saveAndFlush(new TaskTriggerReceiptEntity(UUID.randomUUID().toString(), enterpriseId, taskId, key, fingerprint)); }
        catch (DataIntegrityViolationException duplicate) {
            var replay = triggerReceipts.findByEnterpriseIdAndIdempotencyKey(enterpriseId, key).orElseThrow(() -> duplicate);
            if (!replay.taskId().equals(taskId) || !replay.fingerprint().equals(fingerprint)) throw new TaskIdempotencyConflictException("Idempotency-Key was already used for another task trigger");
            dispatchReserved(task, key, requestId, authentication);
            return;
        }
        dispatchReserved(task, key, requestId, authentication);
    }

    public void dispatchPendingTriggers() {
        for (TaskTriggerReceiptEntity receipt : triggerReceipts.findPending(TaskStatus.ACTIVE,
                org.springframework.data.domain.PageRequest.of(0, 100))) {
            definitions.findById(receipt.taskId()).filter(task -> task.status() == TaskStatus.ACTIVE).ifPresent(task -> {
                try { dispatchReserved(task, receipt.idempotencyKey(), "task-trigger-recovery", null); }
                catch (TaskConflictException unavailable) { /* Keep the durable reservation for the next attempt. */ }
            });
        }
    }

    private void dispatchReserved(TaskDefinitionEntity task, String key, String requestId,
                                  org.springframework.security.core.Authentication authentication) {
        if (executions.findByTaskIdAndIdempotencyKey(task.taskId(), key).isPresent()) return;
        if (!scheduler.dispatch(scheduleOf(task), key)) return;
        audit.recordTenantResourceSuccess(task.enterpriseId(), "task:" + task.taskId(), "TASK_TRIGGERED", requestId, authentication);
    }

    public List<TrustedTaskTypeView> types() { return registry.types(); }

    private void validate(TaskCreateCommand request) {
        if (request == null || request.name() == null || request.name().isBlank()) throw new TaskValidationException("Task name is required");
        if (!registry.supports(request.taskType())) throw new TaskUntrustedTypeException(request.taskType());
        if (request.cronExpression() == null || !scheduler.validCron(request.cronExpression().trim())) throw new TaskValidationException("Cron expression is invalid");
        retries(request.maxRetries()); timeout(request.timeoutMs()); json(request.parameters());
    }
    private static String fingerprint(String taskId) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(taskId.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String json(Map<String, Object> parameters) { if (parameters == null) return "{}"; try { return objectMapper.writeValueAsString(parameters); } catch (JacksonException e) { throw new TaskValidationException("Task parameters are invalid"); } }
    private TaskDefinitionEntity require(String enterpriseId, String taskId) { requireTenant(enterpriseId); return definitions.findById(taskId).filter(t -> enterpriseId.equals(t.enterpriseId())).orElseThrow(TaskNotFoundException::new); }
    private static void requireTenant(String enterpriseId) { if (enterpriseId == null || !enterpriseId.matches("[A-Za-z0-9_-]{1,64}")) throw new TaskNotFoundException(); }
    private static void etag(TaskDefinitionEntity task, String header) { if (header == null || !header.equals("\"" + task.version() + "\"")) throw new TaskPreconditionException(); }
    private TaskView view(TaskDefinitionEntity t) { return new TaskView(t.taskId(), t.enterpriseId(), t.name(), t.taskType(), t.cronExpression(), parameters(t.parametersJson()), t.maxRetries(), t.timeoutMs(), t.status().name(), t.version(), t.createdAt(), t.updatedAt()); }
    @SuppressWarnings("unchecked") private Map<String, Object> parameters(String json) { if (json == null || json.isBlank()) return Map.of(); try { Map<String,Object> m = objectMapper.readValue(json, Map.class); return m == null ? Map.of() : Map.copyOf(m); } catch (RuntimeException ignored) { return Map.of(); } }
    private TaskExecutionView executionView(TaskExecutionEntity e) { return new TaskExecutionView(e.executionId(), e.taskId(), e.enterpriseId(), e.fireInstanceId(), e.status().name(), e.attempt(), e.startedAt(), e.finishedAt(), e.failureCode(), e.idempotencyKey(), e.recoveryCount() > 0, attempts.findAllByExecutionIdOrderByAttemptAsc(e.executionId()).stream().map(a -> new TaskAttemptView(a.attempt(), a.status().name(), a.startedAt(), a.finishedAt(), a.failureCode(), a.nodeId())).toList()); }

    private static TaskSchedule scheduleOf(TaskDefinitionEntity task) { return new TaskSchedule(task.taskId(), task.enterpriseId(), task.cronExpression()); }
}
