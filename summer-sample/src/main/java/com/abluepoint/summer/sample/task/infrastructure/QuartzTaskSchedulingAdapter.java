package com.abluepoint.summer.sample.task.infrastructure;
import org.quartz.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.quartz.autoconfigure.QuartzProperties;
import com.abluepoint.summer.sample.task.domain.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
@Component
public final class QuartzTaskSchedulingAdapter implements TaskSchedulingPort, SmartInitializingSingleton {
 private static final String GROUP = "summer-task";
 private static final String LEGACY_TASK_JOB = "com.abluepoint.summer.sample.task.QuartzTaskJob";
 private static final String LEGACY_RECOVERY_JOB = "com.abluepoint.summer.sample.task.TaskTriggerRecoveryJob";
 private final Scheduler scheduler;
 private final JdbcTemplate jdbc;
 private final String tablePrefix;
 public QuartzTaskSchedulingAdapter(Scheduler scheduler, JdbcTemplate jdbc, QuartzProperties properties) {
  this.scheduler = scheduler;
  this.jdbc = jdbc;
  this.tablePrefix = properties.getProperties().getOrDefault("org.quartz.jobStore.tablePrefix", "QRTZ_");
  if (!tablePrefix.matches("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)?"))
   throw new IllegalArgumentException("Invalid Quartz table prefix");
 }
 @Override
 public void afterSingletonsInstantiated() {
        try {
            if (!scheduler.getMetaData().isJobStoreSupportsPersistence()) return;
            // SmartInitializingSingleton runs before SmartLifecycle starts Quartz. Include paused
            // tasks: application bootstrap only schedules ACTIVE definitions, but their stored
            // triggers must never attempt to load either removed implementation class.
            jdbc.update("UPDATE " + tablePrefix + "JOB_DETAILS SET JOB_CLASS_NAME=? WHERE SCHED_NAME=? AND JOB_GROUP LIKE ? AND JOB_NAME LIKE ? AND JOB_CLASS_NAME=?",
                    QuartzTaskJob.class.getName(), scheduler.getSchedulerName(), GROUP + "-%", "task-%", LEGACY_TASK_JOB);
            migrateLegacyJobClass(new JobKey("trigger-recovery", "summer-internal"), LEGACY_RECOVERY_JOB, TaskTriggerRecoveryJob.class);
        } catch (SchedulerException | DataAccessException unavailable) {
            throw new TaskConflictException("Task scheduler unavailable", unavailable);
        }
 }
 public boolean validCron(String expression) { return CronExpression.isValidExpression(expression); }
 public void initializeRecovery() {
        JobKey recovery = new JobKey("trigger-recovery", "summer-internal");
        try {
            migrateLegacyJobClass(recovery, LEGACY_RECOVERY_JOB, TaskTriggerRecoveryJob.class);
            if (!scheduler.checkExists(recovery)) {
                try {
                    scheduler.scheduleJob(JobBuilder.newJob(TaskTriggerRecoveryJob.class).withIdentity(recovery).requestRecovery().build(),
                            TriggerBuilder.newTrigger().withIdentity("trigger-recovery", "summer-internal").forJob(recovery)
                                    .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(5).repeatForever()).build());
                } catch (ObjectAlreadyExistsException alreadyScheduled) {
                    // One clustered maintenance job services receipts reserved by every application node.
                }
            }
            enableRecovery(recovery);
        } catch (SchedulerException | DataAccessException unavailable) {
            throw new TaskConflictException("Task scheduler unavailable", unavailable);
        }
 }
 public void setStatus(TaskSchedule task, TaskStatus status) {
  try { if (status == TaskStatus.PAUSED) scheduler.pauseJob(jobKey(task)); else { reschedule(task); scheduler.resumeJob(jobKey(task)); } }
  catch (SchedulerException exception) { throw new TaskConflictException("Task scheduler unavailable"); }
 }
 public boolean dispatch(TaskSchedule task, String key) {
  try { scheduler.scheduleJob(TriggerBuilder.newTrigger().withIdentity("manual-" + fingerprint(key), GROUP + "-" + task.enterpriseId()).forJob(jobKey(task)).usingJobData("idempotencyKey", key).startNow().build()); return true; }
  catch (ObjectAlreadyExistsException alreadyQueued) { return false; }
  catch (SchedulerException exception) { throw new TaskConflictException("Task scheduler unavailable"); }
 }
 private static String fingerprint(String key) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private JobKey jobKey(TaskSchedule task) { return new JobKey("task-" + task.taskId(), GROUP + "-" + task.enterpriseId()); }
    private TriggerKey triggerKey(TaskSchedule task) { return new TriggerKey("trigger-" + task.taskId(), GROUP + "-" + task.enterpriseId()); }
    public void scheduleExisting(TaskSchedule task) {
        try {
            if (!scheduler.checkExists(jobKey(task))) schedule(task);
            else {
                migrateLegacyJobClass(jobKey(task), LEGACY_TASK_JOB, QuartzTaskJob.class);
                enableRecovery(jobKey(task));
            }
        } catch (SchedulerException | DataAccessException unavailable) {
            throw new TaskConflictException("Task scheduler unavailable", unavailable);
        }
    }
    private void migrateLegacyJobClass(JobKey key, String legacyClass, Class<? extends Job> currentClass) throws SchedulerException {
        if (!scheduler.getMetaData().isJobStoreSupportsPersistence()) return;
        // Change only the class identifier before Quartz tries to resolve the removed class.
        // JobData, recovery flags and every trigger row remain untouched.
        jdbc.update("UPDATE " + tablePrefix + "JOB_DETAILS SET JOB_CLASS_NAME=? WHERE SCHED_NAME=? AND JOB_NAME=? AND JOB_GROUP=? AND JOB_CLASS_NAME=?",
                currentClass.getName(), scheduler.getSchedulerName(), key.getName(), key.getGroup(), legacyClass);
    }
    private void enableRecovery(JobKey key) throws SchedulerException {
        JobDetail stored = scheduler.getJobDetail(key);
        if (stored != null && !stored.requestsRecovery()) scheduler.addJob(stored.getJobBuilder().requestRecovery().build(), true, true);
    }
    public void schedule(TaskSchedule task) { try { JobDetail job = JobBuilder.newJob(QuartzTaskJob.class).withIdentity(jobKey(task)).usingJobData("taskId", task.taskId()).storeDurably().requestRecovery().build(); CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey(task)).forJob(job).withSchedule(CronScheduleBuilder.cronSchedule(task.cronExpression()).withMisfireHandlingInstructionDoNothing()).build(); scheduler.scheduleJob(job, trigger); } catch (ObjectAlreadyExistsException ignored) { reschedule(task); } catch (SchedulerException exception) { throw new TaskConflictException("Task scheduler unavailable"); } }
    public void reschedule(TaskSchedule task) { try { if (!scheduler.checkExists(jobKey(task))) { schedule(task); return; } scheduler.rescheduleJob(triggerKey(task), TriggerBuilder.newTrigger().withIdentity(triggerKey(task)).forJob(jobKey(task)).withSchedule(CronScheduleBuilder.cronSchedule(task.cronExpression()).withMisfireHandlingInstructionDoNothing()).build()); } catch (SchedulerException exception) { throw new TaskConflictException("Task scheduler unavailable"); } }
}
