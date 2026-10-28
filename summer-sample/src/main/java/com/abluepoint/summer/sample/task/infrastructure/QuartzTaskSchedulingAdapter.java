package com.abluepoint.summer.sample.task.infrastructure;
import org.quartz.*;
import org.springframework.stereotype.Component;
import com.abluepoint.summer.sample.task.domain.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
@Component
public final class QuartzTaskSchedulingAdapter implements TaskSchedulingPort {
 private static final String GROUP = "summer-task";
 private final Scheduler scheduler;
 public QuartzTaskSchedulingAdapter(Scheduler scheduler) { this.scheduler = scheduler; }
 public boolean validCron(String expression) { return CronExpression.isValidExpression(expression); }
 public void initializeRecovery() {
        JobKey recovery = new JobKey("trigger-recovery", "summer-internal");
        try {
            scheduler.scheduleJob(JobBuilder.newJob(TaskTriggerRecoveryJob.class).withIdentity(recovery).requestRecovery().build(),
                    TriggerBuilder.newTrigger().withIdentity("trigger-recovery", "summer-internal").forJob(recovery)
                            .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(5).repeatForever()).build());
        } catch (ObjectAlreadyExistsException alreadyScheduled) {
            // One clustered maintenance job services receipts reserved by every application node.
        } catch (SchedulerException unavailable) {
            throw new TaskConflictException("Task scheduler unavailable");
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
    public void scheduleExisting(TaskSchedule task) { try { if (!scheduler.checkExists(jobKey(task))) schedule(task); else enableRecovery(task); } catch (SchedulerException ignored) { } }
    private void enableRecovery(TaskSchedule task) throws SchedulerException {
        JobDetail stored = scheduler.getJobDetail(jobKey(task));
        if (stored != null && !stored.requestsRecovery()) scheduler.addJob(stored.getJobBuilder().requestRecovery().build(), true);
    }
    public void schedule(TaskSchedule task) { try { JobDetail job = JobBuilder.newJob(QuartzTaskJob.class).withIdentity(jobKey(task)).usingJobData("taskId", task.taskId()).storeDurably().requestRecovery().build(); CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(triggerKey(task)).forJob(job).withSchedule(CronScheduleBuilder.cronSchedule(task.cronExpression()).withMisfireHandlingInstructionDoNothing()).build(); scheduler.scheduleJob(job, trigger); } catch (ObjectAlreadyExistsException ignored) { reschedule(task); } catch (SchedulerException exception) { throw new TaskConflictException("Task scheduler unavailable"); } }
    public void reschedule(TaskSchedule task) { try { if (!scheduler.checkExists(jobKey(task))) { schedule(task); return; } scheduler.rescheduleJob(triggerKey(task), TriggerBuilder.newTrigger().withIdentity(triggerKey(task)).forJob(jobKey(task)).withSchedule(CronScheduleBuilder.cronSchedule(task.cronExpression()).withMisfireHandlingInstructionDoNothing()).build()); } catch (SchedulerException exception) { throw new TaskConflictException("Task scheduler unavailable"); } }
}
