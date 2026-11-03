package com.abluepoint.summer.sample.task;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;
import com.abluepoint.summer.sample.customer.CustomerAuditService;
import org.quartz.*;
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
class TaskSchedulerService {
    private static final String GROUP = "summer-task";
    private final TaskDefinitionRepository definitions;
    private final TaskExecutionRepository executions;
    private final TaskAttemptRepository attempts;
    private final TaskTriggerReceiptRepository triggerReceipts;
    private final TrustedTaskRegistry registry;
    private final Scheduler scheduler;
    private final ObjectMapper objectMapper;
    private final CustomerAuditService audit;

    TaskSchedulerService(TaskDefinitionRepository definitions, TaskExecutionRepository executions,
                         TaskAttemptRepository attempts, TaskTriggerReceiptRepository triggerReceipts,
                         TrustedTaskRegistry registry, Scheduler scheduler, ObjectMapper objectMapper,
                         CustomerAuditService audit) {
        this.definitions = definitions; this.executions = executions; this.attempts = attempts; this.triggerReceipts = triggerReceipts; this.registry = registry;
        this.scheduler = scheduler; this.objectMapper = objectMapper; this.audit = audit;
    }

    @EventListener(ApplicationReadyEvent.class)
    void schedulePersistedTasks() {
        definitions.findAllByStatus(TaskStatus.ACTIVE).forEach(this::scheduleExisting);
        JobKey recovery = new JobKey("trigger-recovery", "summer-internal");
        try {
            scheduler.scheduleJob(JobBuilder.newJob(TaskTriggerRecoveryJob.class).withIdentity(recovery).requestRecovery().build(),
                    TriggerBuilder.newTrigger().withIdentity("trigger-recovery", "summer-internal").forJob(recovery)
                            .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(5).repeatForever()).build());
        } catch (ObjectAlreadyExistsException alreadyScheduled) {
            // One clustered maintenance job services receipts reserved by every application node.
        } catch (SchedulerException unavailable) {
            throw SummerException.withMessage(TaskErrorCode.CONFLICT, "Task scheduler unavailable", unavailable);
        }
    }

    @Transactional(readOnly = true)
    List<TaskView> list(String enterpriseId) {
        requireTenant(enterpriseId);
        return definitions.findAllByEnterpriseIdOrderByUpdatedAtDesc(enterpriseId).stream().map(this::view).toList();
    }

    @Transactional
    TaskView create(String enterpriseId, TaskCreateRequest request, String requestId, org.springframework.security.core.Authentication authentication) {
        requireTenant(enterpriseId); validate(request);
        if (definitions.findByEnterpriseIdAndName(enterpriseId, request.name().trim()).isPresent()) throw SummerException.withMessage(TaskErrorCode.CONFLICT, "Task name already exists");
        String id = UUID.randomUUID().toString();
        TaskDefinitionEntity task = new TaskDefinitionEntity(id, enterpriseId, request.name().trim(), request.taskType().trim(),
                request.cronExpression().trim(), json(request.parameters()), retries(request.maxRetries()), timeout(request.timeoutMs()));
        TaskView result = view(definitions.saveAndFlush(task));
        schedule(task);
        audit.recordTenantResourceSuccess(enterpriseId, "task:" + id, "TASK_CREATED", requestId, authentication);
        return result;
    }

    @Transactional
    TaskView update(String enterpriseId, String taskId, TaskUpdateRequest request, String ifMatch, String requestId,
                    org.springframework.security.core.Authentication authentication) {
        TaskDefinitionEntity task = require(enterpriseId, taskId); etag(task, ifMatch);
        if (request == null) throw SummerException.withMessage(TaskErrorCode.INVALID, "Task update is required");
        String name = request.name() == null ? task.name() : request.name().trim();
        String cron = request.cronExpression() == null ? task.cronExpression() : request.cronExpression().trim();
        if (name.isBlank() || !CronExpression.isValidExpression(cron)) throw SummerException.withMessage(TaskErrorCode.INVALID, "Task name or cron expression is invalid");
        int maxRetries = request.maxRetries() == null ? task.maxRetries() : request.maxRetries();
        if (maxRetries < 0 || maxRetries > 10) throw SummerException.withMessage(TaskErrorCode.INVALID, "maxRetries must be between 0 and 10");
        long timeoutMs = request.timeoutMs() == null ? task.timeoutMs() : timeout(request.timeoutMs());
        task.update(name, cron, request.parameters() == null ? task.parametersJson() : json(request.parameters()), maxRetries, timeoutMs);
        TaskView result = view(definitions.saveAndFlush(task));
        reschedule(task);
        audit.recordTenantResourceSuccess(enterpriseId, "task:" + taskId, "TASK_UPDATED", requestId, authentication);
        return result;
    }

    @Transactional
    TaskView setStatus(String enterpriseId, String taskId, TaskStatus status, String ifMatch, String requestId,
                       org.springframework.security.core.Authentication authentication) {
        TaskDefinitionEntity task = require(enterpriseId, taskId); etag(task, ifMatch); task.status(status);
        TaskView result = view(definitions.saveAndFlush(task));
        try { if (status == TaskStatus.PAUSED) scheduler.pauseJob(jobKey(task)); else { reschedule(task); scheduler.resumeJob(jobKey(task)); } }
        catch (SchedulerException exception) { throw SummerException.withMessage(TaskErrorCode.CONFLICT, "Task scheduler unavailable", exception); }
        audit.recordTenantResourceSuccess(enterpriseId, "task:" + taskId, status == TaskStatus.PAUSED ? "TASK_PAUSED" : "TASK_RESUMED", requestId, authentication);
        return result;
    }

    @Transactional(readOnly = true)
    TaskExecutionPage executions(String enterpriseId, String taskId) {
        require(enterpriseId, taskId);
        List<TaskExecutionView> values = executions.findAllByEnterpriseIdAndTaskIdOrderByStartedAtDesc(enterpriseId, taskId).stream().map(this::executionView).toList();
        return new TaskExecutionPage(values, values.size());
    }

    void trigger(String enterpriseId, String taskId, String idempotencyKey, String requestId, org.springframework.security.core.Authentication authentication) {
        TaskDefinitionEntity task = require(enterpriseId, taskId);
        if (task.status() != TaskStatus.ACTIVE) throw SummerException.withMessage(TaskErrorCode.CONFLICT, "Paused task cannot be triggered");
        String key = requireKey(idempotencyKey);
        String fingerprint = fingerprint(taskId);
        var prior = triggerReceipts.findByEnterpriseIdAndIdempotencyKey(enterpriseId, key);
        if (prior.isPresent()) {
            if (!prior.get().fingerprint().equals(fingerprint) || !prior.get().taskId().equals(taskId))
                throw SummerException.withMessage(TaskErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency-Key was already used for another task trigger");
            dispatchReserved(task, key, requestId, authentication);
            return;
        }
        try { triggerReceipts.saveAndFlush(new TaskTriggerReceiptEntity(UUID.randomUUID().toString(), enterpriseId, taskId, key, fingerprint)); }
        catch (DataIntegrityViolationException duplicate) {
            var replay = triggerReceipts.findByEnterpriseIdAndIdempotencyKey(enterpriseId, key).orElseThrow(() -> duplicate);
            if (!replay.taskId().equals(taskId) || !replay.fingerprint().equals(fingerprint)) throw SummerException.withMessage(TaskErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency-Key was already used for another task trigger");
            dispatchReserved(task, key, requestId, authentication);
            return;
        }
        dispatchReserved(task, key, requestId, authentication);
    }

    void dispatchPendingTriggers() {
        for (TaskTriggerReceiptEntity receipt : triggerReceipts.findPending(TaskStatus.ACTIVE,
                org.springframework.data.domain.PageRequest.of(0, 100))) {
            definitions.findById(receipt.taskId()).filter(task -> task.status() == TaskStatus.ACTIVE).ifPresent(task -> {
                try { dispatchReserved(task, receipt.idempotencyKey(), "task-trigger-recovery", null); }
                catch (SummerException unavailable) {
                    if (unavailable.errorCode() != TaskErrorCode.CONFLICT) throw unavailable;
                    // Keep the durable reservation for the next attempt.
                }
            });
        }
    }

    private void dispatchReserved(TaskDefinitionEntity task, String key, String requestId,
                                  org.springframework.security.core.Authentication authentication) {
        if (executions.findByTaskIdAndIdempotencyKey(task.taskId(), key).isPresent()) return;
        try {
            scheduler.scheduleJob(TriggerBuilder.newTrigger()
                    .withIdentity("manual-" + fingerprint(key), GROUP + "-" + task.enterpriseId())
                    .forJob(jobKey(task)).usingJobData("idempotencyKey", key).startNow().build());
        }
        catch (ObjectAlreadyExistsException alreadyQueued) { return; }
        catch (SchedulerException exception) { throw SummerException.withMessage(TaskErrorCode.CONFLICT, "Task scheduler unavailable", exception); }
        audit.recordTenantResourceSuccess(task.enterpriseId(), "task:" + task.taskId(), "TASK_TRIGGERED", requestId, authentication);
    }

    List<TrustedTaskTypeView> types() { return registry.types(); }

    private void validate(TaskCreateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) throw SummerException.withMessage(TaskErrorCode.INVALID, "Task name is required");
        if (!registry.supports(request.taskType())) throw SummerException.withMessage(TaskErrorCode.TYPE_UNTRUSTED, "Task type is not trusted: " + request.taskType());
        if (request.cronExpression() == null || !CronExpression.isValidExpression(request.cronExpression().trim())) throw SummerException.withMessage(TaskErrorCode.INVALID, "Cron expression is invalid");
        retries(request.maxRetries()); timeout(request.timeoutMs()); json(request.parameters());
    }
    private static int retries(Integer value) { int n = value == null ? 0 : value; if (n < 0 || n > 10) throw SummerException.withMessage(TaskErrorCode.INVALID, "maxRetries must be between 0 and 10"); return n; }
    private static long timeout(Long value) { long n = value == null ? 30_000L : value; if (n < 1_000L || n > 300_000L) throw SummerException.withMessage(TaskErrorCode.INVALID, "timeoutMs must be between 1000 and 300000"); return n; }
    private static String requireKey(String value) { if (value == null || value.isBlank() || value.length() > 255) throw new SummerException(TaskErrorCode.IDEMPOTENCY_REQUIRED); return value.trim(); }
    private static String fingerprint(String taskId) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(taskId.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String json(Map<String, Object> parameters) { if (parameters == null) return "{}"; try { return objectMapper.writeValueAsString(parameters); } catch (JacksonException e) { throw SummerException.withMessage(TaskErrorCode.INVALID, "Task parameters are invalid", e); } }
    private TaskDefinitionEntity require(String enterpriseId, String taskId) { requireTenant(enterpriseId); return definitions.findById(taskId).filter(t -> enterpriseId.equals(t.enterpriseId())).orElseThrow(() -> SummerException.withMessage(CommonErrorCode.NOT_FOUND, "Task not found")); }
    private static void requireTenant(String enterpriseId) { if (enterpriseId == null || !enterpriseId.matches("[A-Za-z0-9_-]{1,64}")) throw SummerException.withMessage(CommonErrorCode.NOT_FOUND, "Task not found"); }
    private static void etag(TaskDefinitionEntity task, String header) { if (header == null || !header.equals("\"" + task.version() + "\"")) throw SummerException.withMessage(SampleTransportErrorCode.PRECONDITION_FAILED, "The task was changed by another request"); }
    private TaskView view(TaskDefinitionEntity t) { return new TaskView(t.taskId(), t.enterpriseId(), t.name(), t.taskType(), t.cronExpression(), parameters(t.parametersJson()), t.maxRetries(), t.timeoutMs(), t.status().name(), t.version(), t.createdAt(), t.updatedAt()); }
    @SuppressWarnings("unchecked") private Map<String, Object> parameters(String json) { if (json == null || json.isBlank()) return Map.of(); try { Map<String,Object> m = objectMapper.readValue(json, Map.class); return m == null ? Map.of() : Map.copyOf(m); } catch (RuntimeException ignored) { return Map.of(); } }
    private TaskExecutionView executionView(TaskExecutionEntity e) { return new TaskExecutionView(e.executionId(), e.taskId(), e.enterpriseId(), e.fireInstanceId(), e.status().name(), e.attempt(), e.startedAt(), e.finishedAt(), e.failureCode(), e.idempotencyKey(), e.recoveryCount() > 0, attempts.findAllByExecutionIdOrderByAttemptAsc(e.executionId()).stream().map(a -> new TaskAttemptView(a.attempt(), a.status().name(), a.startedAt(), a.finishedAt(), a.failureCode(), a.nodeId())).toList()); }
    private JobKey jobKey(TaskDefinitionEntity task) { return new JobKey("task-" + task.taskId(), GROUP + "-" + task.enterpriseId()); }
    private TriggerKey triggerKey(TaskDefinitionEntity task) { return new TriggerKey("trigger-" + task.taskId(), GROUP + "-" + task.enterpriseId()); }
    private void scheduleExisting(TaskDefinitionEntity task) { try { if (!scheduler.checkExists(jobKey(task))) schedule(task); else enableRecovery(task); } catch (SchedulerException ignored) { } }
    private void enableRecovery(TaskDefinitionEntity task) throws SchedulerException {
        JobDetail stored = scheduler.getJobDetail(jobKey(task));
        if (stored != null && !stored.requestsRecovery()) scheduler.addJob(stored.getJobBuilder().requestRecovery().build(), true);
    }
    private void schedule(TaskDefinitionEntity task) { try { JobDetail job = JobBuilder.newJob(QuartzTaskJob.class).withIdentity(jobKey(task)).usingJobData("taskId", task.taskId()).storeDurably().requestRecovery().build(); CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey(task)).forJob(job).withSchedule(CronScheduleBuilder.cronSchedule(task.cronExpression()).withMisfireHandlingInstructionDoNothing()).build(); scheduler.scheduleJob(job, trigger); } catch (ObjectAlreadyExistsException ignored) { reschedule(task); } catch (SchedulerException exception) { throw SummerException.withMessage(TaskErrorCode.CONFLICT, "Task scheduler unavailable", exception); } }
    private void reschedule(TaskDefinitionEntity task) { try { if (!scheduler.checkExists(jobKey(task))) { schedule(task); return; } scheduler.rescheduleJob(triggerKey(task), TriggerBuilder.newTrigger().withIdentity(triggerKey(task)).forJob(jobKey(task)).withSchedule(CronScheduleBuilder.cronSchedule(task.cronExpression()).withMisfireHandlingInstructionDoNothing()).build()); } catch (SchedulerException exception) { throw SummerException.withMessage(TaskErrorCode.CONFLICT, "Task scheduler unavailable", exception); } }
}
