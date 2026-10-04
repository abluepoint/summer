package com.abluepoint.summer.sample.task;

import com.abluepoint.summer.sample.customer.CustomerAuditService;
import org.quartz.*;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Service
class TaskSchedulerService {
    static final String TENANT_ID = "ent-acme";
    private static final String GROUP = "summer-task";
    private final TaskDefinitionRepository definitions;
    private final TaskExecutionRepository executions;
    private final TrustedTaskRegistry registry;
    private final Scheduler scheduler;
    private final ObjectMapper objectMapper;
    private final CustomerAuditService audit;

    TaskSchedulerService(TaskDefinitionRepository definitions, TaskExecutionRepository executions,
                         TrustedTaskRegistry registry, Scheduler scheduler, ObjectMapper objectMapper,
                         CustomerAuditService audit) {
        this.definitions = definitions; this.executions = executions; this.registry = registry;
        this.scheduler = scheduler; this.objectMapper = objectMapper; this.audit = audit;
    }

    @EventListener(ApplicationReadyEvent.class)
    void schedulePersistedTasks() {
        definitions.findAllByEnterpriseIdOrderByUpdatedAtDesc(TENANT_ID).stream()
                .filter(task -> task.status() == TaskStatus.ACTIVE).forEach(this::scheduleExisting);
    }

    @Transactional(readOnly = true)
    List<TaskView> list(String enterpriseId) {
        requireTenant(enterpriseId);
        return definitions.findAllByEnterpriseIdOrderByUpdatedAtDesc(enterpriseId).stream().map(this::view).toList();
    }

    @Transactional
    TaskView create(String enterpriseId, TaskCreateRequest request, String requestId, org.springframework.security.core.Authentication authentication) {
        requireTenant(enterpriseId); validate(request);
        if (definitions.findByEnterpriseIdAndName(enterpriseId, request.name().trim()).isPresent()) throw new TaskConflictException("Task name already exists");
        String id = UUID.randomUUID().toString();
        TaskDefinitionEntity task = new TaskDefinitionEntity(id, enterpriseId, request.name().trim(), request.taskType().trim(),
                request.cronExpression().trim(), json(request.parameters()), retries(request.maxRetries()));
        TaskView result = view(definitions.saveAndFlush(task));
        schedule(task);
        audit.recordResourceSuccess("task:" + id, "TASK_CREATED", requestId, authentication);
        return result;
    }

    @Transactional
    TaskView update(String enterpriseId, String taskId, TaskUpdateRequest request, String ifMatch, String requestId,
                    org.springframework.security.core.Authentication authentication) {
        TaskDefinitionEntity task = require(enterpriseId, taskId); etag(task, ifMatch);
        if (request == null) throw new TaskValidationException("Task update is required");
        String name = request.name() == null ? task.name() : request.name().trim();
        String cron = request.cronExpression() == null ? task.cronExpression() : request.cronExpression().trim();
        if (name.isBlank() || !CronExpression.isValidExpression(cron)) throw new TaskValidationException("Task name or cron expression is invalid");
        int maxRetries = request.maxRetries() == null ? task.maxRetries() : request.maxRetries();
        if (maxRetries < 0 || maxRetries > 10) throw new TaskValidationException("maxRetries must be between 0 and 10");
        task.update(name, cron, request.parameters() == null ? task.parametersJson() : json(request.parameters()), maxRetries);
        TaskView result = view(definitions.saveAndFlush(task));
        reschedule(task);
        audit.recordResourceSuccess("task:" + taskId, "TASK_UPDATED", requestId, authentication);
        return result;
    }

    @Transactional
    TaskView setStatus(String enterpriseId, String taskId, TaskStatus status, String ifMatch, String requestId,
                       org.springframework.security.core.Authentication authentication) {
        TaskDefinitionEntity task = require(enterpriseId, taskId); etag(task, ifMatch); task.status(status);
        TaskView result = view(definitions.saveAndFlush(task));
        try { if (status == TaskStatus.PAUSED) scheduler.pauseJob(jobKey(task)); else { reschedule(task); scheduler.resumeJob(jobKey(task)); } }
        catch (SchedulerException exception) { throw new TaskConflictException("Task scheduler unavailable"); }
        audit.recordResourceSuccess("task:" + taskId, status == TaskStatus.PAUSED ? "TASK_PAUSED" : "TASK_RESUMED", requestId, authentication);
        return result;
    }

    @Transactional(readOnly = true)
    TaskExecutionPage executions(String enterpriseId, String taskId) {
        require(enterpriseId, taskId);
        List<TaskExecutionView> values = executions.findAllByEnterpriseIdAndTaskIdOrderByStartedAtDesc(enterpriseId, taskId).stream().map(this::executionView).toList();
        return new TaskExecutionPage(values, values.size());
    }

    void trigger(String enterpriseId, String taskId, String requestId, org.springframework.security.core.Authentication authentication) {
        TaskDefinitionEntity task = require(enterpriseId, taskId);
        if (task.status() != TaskStatus.ACTIVE) throw new TaskConflictException("Paused task cannot be triggered");
        try { scheduler.triggerJob(jobKey(task)); }
        catch (SchedulerException exception) { throw new TaskConflictException("Task scheduler unavailable"); }
        audit.recordResourceSuccess("task:" + taskId, "TASK_TRIGGERED", requestId, authentication);
    }

    List<TrustedTaskTypeView> types() { return registry.types(); }

    private void validate(TaskCreateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) throw new TaskValidationException("Task name is required");
        if (!registry.supports(request.taskType())) throw new TaskUntrustedTypeException(request.taskType());
        if (request.cronExpression() == null || !CronExpression.isValidExpression(request.cronExpression().trim())) throw new TaskValidationException("Cron expression is invalid");
        retries(request.maxRetries()); json(request.parameters());
    }
    private static int retries(Integer value) { int n = value == null ? 0 : value; if (n < 0 || n > 10) throw new TaskValidationException("maxRetries must be between 0 and 10"); return n; }
    private String json(Map<String, Object> parameters) { if (parameters == null) return "{}"; try { return objectMapper.writeValueAsString(parameters); } catch (JacksonException e) { throw new TaskValidationException("Task parameters are invalid"); } }
    private TaskDefinitionEntity require(String enterpriseId, String taskId) { requireTenant(enterpriseId); return definitions.findById(taskId).filter(t -> enterpriseId.equals(t.enterpriseId())).orElseThrow(TaskNotFoundException::new); }
    private static void requireTenant(String enterpriseId) { if (!TENANT_ID.equals(enterpriseId)) throw new TaskNotFoundException(); }
    private static void etag(TaskDefinitionEntity task, String header) { if (header == null || !header.equals("\"" + task.version() + "\"")) throw new TaskPreconditionException(); }
    private TaskView view(TaskDefinitionEntity t) { return new TaskView(t.taskId(), t.enterpriseId(), t.name(), t.taskType(), t.cronExpression(), parameters(t.parametersJson()), t.maxRetries(), t.status().name(), t.version(), t.createdAt(), t.updatedAt()); }
    @SuppressWarnings("unchecked") private Map<String, Object> parameters(String json) { if (json == null || json.isBlank()) return Map.of(); try { Map<String,Object> m = objectMapper.readValue(json, Map.class); return m == null ? Map.of() : Map.copyOf(m); } catch (RuntimeException ignored) { return Map.of(); } }
    private TaskExecutionView executionView(TaskExecutionEntity e) { return new TaskExecutionView(e.executionId(), e.taskId(), e.enterpriseId(), e.fireInstanceId(), e.status().name(), e.attempt(), e.startedAt(), e.finishedAt(), e.failureCode()); }
    private JobKey jobKey(TaskDefinitionEntity task) { return new JobKey("task-" + task.taskId(), GROUP + "-" + task.enterpriseId()); }
    private TriggerKey triggerKey(TaskDefinitionEntity task) { return new TriggerKey("trigger-" + task.taskId(), GROUP + "-" + task.enterpriseId()); }
    private void scheduleExisting(TaskDefinitionEntity task) { try { if (!scheduler.checkExists(jobKey(task))) schedule(task); } catch (SchedulerException ignored) { } }
    private void schedule(TaskDefinitionEntity task) { try { JobDetail job = JobBuilder.newJob(QuartzTaskJob.class).withIdentity(jobKey(task)).usingJobData("taskId", task.taskId()).storeDurably().build(); CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey(task)).forJob(job).withSchedule(CronScheduleBuilder.cronSchedule(task.cronExpression()).withMisfireHandlingInstructionDoNothing()).build(); scheduler.scheduleJob(job, trigger); } catch (ObjectAlreadyExistsException ignored) { reschedule(task); } catch (SchedulerException exception) { throw new TaskConflictException("Task scheduler unavailable"); } }
    private void reschedule(TaskDefinitionEntity task) { try { if (!scheduler.checkExists(jobKey(task))) { schedule(task); return; } scheduler.rescheduleJob(triggerKey(task), TriggerBuilder.newTrigger().withIdentity(triggerKey(task)).forJob(jobKey(task)).withSchedule(CronScheduleBuilder.cronSchedule(task.cronExpression()).withMisfireHandlingInstructionDoNothing()).build()); } catch (SchedulerException exception) { throw new TaskConflictException("Task scheduler unavailable"); } }
}
